/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.enrichment.async;

import static com.amazonaws.services.transcribe.model.TranscriptionJobStatus.FAILED;
import static com.amazonaws.services.transcribe.model.TranscriptionJobStatus.IN_PROGRESS;
import static org.nuxeo.ecm.platform.video.VideoConstants.VIDEO_FACET;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.nuxeo.ai.enrichment.AbstractEnrichmentProvider;
import org.nuxeo.ai.enrichment.EnrichmentDescriptor;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentUtils;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.transcribe.AudioTranscription;
import org.nuxeo.ai.transcribe.TranscribeService;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.video.TranscodedVideo;
import org.nuxeo.ecm.platform.video.VideoDocument;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;
import com.amazonaws.services.transcribe.model.GetTranscriptionJobRequest;
import com.amazonaws.services.transcribe.model.GetTranscriptionJobResult;
import com.amazonaws.services.transcribe.model.StartTranscriptionJobResult;
import com.amazonaws.services.transcribe.model.TranscriptionJob;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TranscribeEnrichmentProvider extends AbstractEnrichmentProvider {

    public static final String PROVIDER_NAME = "aws.transcribe";

    public static final String PROVIDER_KIND = "/tagging/transcribe";

    public static final String LANGUAGES_OPTION = "languages";

    public static final String DEFAULT_CONVERSION = "WAV 16K";

    private static final long TIMEOUT = 1000 * 60 * 60 * 2; // 2h

    private static final long WAIT_TIME = 1000 * 5; // 5s

    private static final Logger log = LogManager.getLogger(TranscribeEnrichmentProvider.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected String[] languages;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        languages = descriptor.options.getOrDefault(LANGUAGES_OPTION, "").split(",");
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobDoc) {
        if (!VIDEO_FACET.equals(blobDoc.getPrimaryType()) && !blobDoc.getFacets().contains(VIDEO_FACET)) {
            return null;
        }

        String docId = blobDoc.getId();
        Blob blob = TransactionHelper.runInTransaction(
                () -> CoreInstance.doPrivileged(blobDoc.getRepositoryName(), session -> {
                    DocumentModel doc = session.getDocument(new IdRef(docId));
                    VideoDocument video = doc.getAdapter(VideoDocument.class);
                    if (video == null) {
                        throw new NuxeoException("Document is not a Video document; id = " + docId);
                    }

                    TranscodedVideo tv = video.getTranscodedVideo(DEFAULT_CONVERSION);
                    if (tv != null) {
                        return tv.getBlob();
                    }

                    return null;
                }));

        if (blob == null) {
            throw new NuxeoException("WAV is not ready; doc id = " + blobDoc.getId());
        }

        TranscribeService ts = Framework.getService(TranscribeService.class);
        StartTranscriptionJobResult result = ts.requestTranscription(blob, languages);
        TranscriptionJob job = result.getTranscriptionJob();
        job = awaitJob(docId, ts, job);
        if (FAILED.name().equals(job.getTranscriptionJobStatus())) {
            throw new NuxeoException("Transcribe job failed with reason: " + job.getFailureReason() + "; Job: "
                    + job.getTranscriptionJobName() + " Document Id: " + docId);
        }

        String json = getResponse(docId, job);
        AudioTranscription transcription = getAudioTranscription(docId, json);
        List<AIMetadata.Label> labels = ts.asLabels(transcription);

        List<LabelSuggestion> labelSuggestions = Collections.singletonList(
                new LabelSuggestion(UNSET + PROVIDER_NAME, labels));
        String rawKey = EnrichmentUtils.saveRawBlob(Blobs.createJSONBlob(json), "default");
        EnrichmentMetadata metadata = new EnrichmentMetadata.Builder(PROVIDER_KIND, PROVIDER_NAME, blobDoc).withLabels(
                labelSuggestions).withRawKey(rawKey).build();

        return Collections.singletonList(metadata);
    }

    private AudioTranscription getAudioTranscription(String docId, String json) {
        AudioTranscription transcription;
        try {
            transcription = OBJECT_MAPPER.readValue(json, AudioTranscription.class);
        } catch (IOException e) {
            log.error("Could not process JSON response {}", json, e);
            throw new NuxeoException("Could not read `AudioTranscription` for Document Id: " + docId);
        }

        return transcription;
    }

    private String getResponse(String docId, TranscriptionJob job) {
        String transcriptUri = job.getTranscript().getTranscriptFileUri();
        HttpGet req = new HttpGet(transcriptUri);

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
                CloseableHttpResponse resp = httpClient.execute(req)) {
            HttpEntity entity = resp.getEntity();
            return EntityUtils.toString(entity);
        } catch (IOException e) {
            log.error(e);
            throw new NuxeoException(
                    "Could not retrieve result for Job " + job.getTranscriptionJobName() + " Document Id: " + docId);
        }
    }

    @NotNull
    private TranscriptionJob awaitJob(String docId, TranscribeService ts, TranscriptionJob job) {
        long timeSpent = 0;
        String jobName = job.getTranscriptionJobName();
        GetTranscriptionJobRequest jobRequest = new GetTranscriptionJobRequest().withTranscriptionJobName(jobName);
        while (IN_PROGRESS.name().equals(job.getTranscriptionJobStatus())) {
            GetTranscriptionJobResult jobResult = ts.getClient().getTranscriptionJob(jobRequest);
            job = jobResult.getTranscriptionJob();
            if (timeSpent > TIMEOUT) {
                throw new NuxeoException("Work reached timeout; Job name: " + jobName + " Document Id: " + docId);
            }
            try {
                Thread.sleep(WAIT_TIME);
                timeSpent += WAIT_TIME;
            } catch (InterruptedException e) {
                log.error(e);
                throw new NuxeoException(
                        "Transcribe was interrupted; could not get results for Job: " + jobName + " Document Id: "
                                + docId, e);
            }
        }
        return job;
    }
}
