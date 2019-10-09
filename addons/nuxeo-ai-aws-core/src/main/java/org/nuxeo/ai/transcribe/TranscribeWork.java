/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.transcribe;

import static com.amazonaws.services.transcribe.model.TranscriptionJobStatus.FAILED;
import static com.amazonaws.services.transcribe.model.TranscriptionJobStatus.IN_PROGRESS;
import static org.nuxeo.ai.AIConstants.TRANSCRIBE_FACET;

import java.io.IOException;
import java.io.Serializable;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.ecm.platform.video.TranscodedVideo;
import org.nuxeo.ecm.platform.video.VideoDocument;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

import com.amazonaws.services.transcribe.model.GetTranscriptionJobRequest;
import com.amazonaws.services.transcribe.model.GetTranscriptionJobResult;
import com.amazonaws.services.transcribe.model.LanguageCode;
import com.amazonaws.services.transcribe.model.StartTranscriptionJobResult;
import com.amazonaws.services.transcribe.model.TranscriptionJob;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TranscribeWork extends AbstractWork {

    private static final Logger log = LogManager.getLogger(TranscribeWork.class);

    private static final String CATEGORY = "transcribeWork";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final long TIMEOUT = 1000 * 60 * 60 * 2; // 2h

    private static final long WAIT_TIME = 1000 * 5; // 5sec

    public static final String TRANSCRIBE_LABEL_PROP = "transcribe:label";

    public static final String TRANSCRIBE_RAW_PROP = "transcribe:raw";

    public static final String TRANSCRIBE_NORM_PROP = "transcribe:normalized";

    public static final String START_TIME_FIELD = "start";

    public static final String END_TIME_FIELD = "end";

    public static final String TYPE_FIELD = "type";

    public static final String CONTENT_FIELD = "content";

    public static final String TRANSCRIPT_FIELD = "transcript";

    public static final String ITEMS_FIELD = "items";

    public static final String DEFAULT_CONVERSION = "WAV 16K";

    public static final LanguageCode DEFAULT_LANG_CODE = LanguageCode.EnUS;

    protected final String TITLE;

    public TranscribeWork(String repository, String docId) {
        super(CATEGORY + "_" + docId);
        this.repositoryName = repository;
        this.docId = docId;
        TITLE = CATEGORY + "_" + docId;
    }

    @Override
    public void work() {
        openSystemSession();
        IdRef docRef = new IdRef(docId);
        DocumentModel doc = session.getDocument(docRef);

        VideoDocument video = doc.getAdapter(VideoDocument.class);
        if (video == null) {
            throw new NuxeoException("Document is not a Video document; id = " + docId);
        }

        TranscodedVideo wav = video.getTranscodedVideo(DEFAULT_CONVERSION);
        if (wav == null) {
            throw new NuxeoException("No supported audio conversion found. Transcribe requires " + DEFAULT_CONVERSION);
        }

        setStatus("Starting transcribeRaw");

        TranscribeService ts = Framework.getService(TranscribeService.class);

        StartTranscriptionJobResult result;
        result = ts.transcribe(wav.getBlob(), DEFAULT_LANG_CODE);


        TranscriptionJob job = result.getTranscriptionJob();
        String jobName = job.getTranscriptionJobName();

        GetTranscriptionJobRequest jobRequest = new GetTranscriptionJobRequest()
                .withTranscriptionJobName(jobName);

        long timeSpent = 0;
        while (IN_PROGRESS.name().equals(job.getTranscriptionJobStatus())) {
            GetTranscriptionJobResult jobResult = ts.getClient().getTranscriptionJob(jobRequest);
            job = jobResult.getTranscriptionJob();
            if (timeSpent > TIMEOUT) {
                throw new NuxeoException("Work reached timeout; Job name: "
                        + jobName
                        + " Document Id: " + docId);
            }
            try {
                Thread.sleep(WAIT_TIME);
                timeSpent += WAIT_TIME;
            } catch (InterruptedException e) {
                log.error(e);
                throw new NuxeoException("Transcribe was interrupted; could not get results for Job: "
                        + jobName
                        + " Document Id: " + docId, e);
            }
        }

        String jobStatus = job.getTranscriptionJobStatus();
        if (FAILED.name().equals(jobStatus)) {
            throw new NuxeoException("Transcribe job failed with reason: "
                    + job.getFailureReason()
                    + "; Job: "
                    + job.getTranscriptionJobName()
                    + " Document Id: " + docId);
        }

        String transcriptUri = job.getTranscript().getTranscriptFileUri();
        HttpGet req = new HttpGet(transcriptUri);

        AmazonTranscription transcription;
        String json;
        try (CloseableHttpClient httpClient = HttpClients.createDefault(); CloseableHttpResponse resp = httpClient.execute(req)) {
            HttpEntity entity = resp.getEntity();
            json = EntityUtils.toString(entity);
            transcription = OBJECT_MAPPER.readValue(json, AmazonTranscription.class);
        } catch (IOException e) {
            log.error(e);
            throw new NuxeoException("Could not retrieve result for Job "
                    + job.getTranscriptionJobName()
                    + " Document Id: " + docId);
        }

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        doc = session.getDocument(docRef);
        if (!doc.hasFacet(TRANSCRIBE_FACET)) {
            doc.addFacet(TRANSCRIBE_FACET);
        }

        Blob jsonBlob = Blobs.createJSONBlob(json);
        doc.setPropertyValue(TRANSCRIBE_RAW_PROP, (Serializable) jsonBlob);
        doc.setPropertyValue(TRANSCRIBE_LABEL_PROP, String.join(" ", transcription.getTranscripts()));
        doc.setPropertyValue(TRANSCRIBE_NORM_PROP, (Serializable) jsonBlob);

        session.saveDocument(doc);
    }

//    protected Pair<String, Blob> getTranscribeResults(List<Alternative> alternatives) throws JsonProcessingException {
//        StringBuilder builder = new StringBuilder();
//        List<Map<String, Object>> items = new LinkedList<>();
//        for (Alternative alter : alternatives) {
//            String transcript = alter.transcript();
//            builder.append(transcript)
//                    .append("\n");
//
//            List<Map<String, Object>> elems = buildItems(alter);
//            items.addAll(elems);
//        }
//
//        String json = OBJECT_MAPPER.writeValueAsString(items);
//        JSONBlob blob = new JSONBlob(json);
//        return new ImmutablePair<>(builder.toString(), blob);
//    }
//
//    protected Blob getRawData(List<Alternative> alternatives) throws JsonProcessingException {
//        List<Map<String, Object>> alts = serializeAlternatives(alternatives);
//        String json = OBJECT_MAPPER.writeValueAsString(alts);
//        return new JSONBlob(json);
//    }
//
//    private List<Map<String, Object>> serializeAlternatives(List<Alternative> alternatives) {
//        List<Map<String, Object>> alts = new LinkedList<>();
//        for (Alternative alter : alternatives) {
//            Map<String, Object> alt = new HashMap<>();
//            String transcript = alter.transcript();
//            List<Map<String, Object>> items = buildItems(alter);
//            alt.put(TRANSCRIPT_FIELD, transcript);
//            alt.put(ITEMS_FIELD, items);
//            alts.add(alt);
//        }
//        return alts;
//    }
//
//    private List<Map<String, Object>> buildItems(Alternative alter) {
//        return alter.items().stream()
//                .map(i -> {
//                    Map<String, Object> map = new HashMap<>();
//                    map.put(START_TIME_FIELD, i.startTime());
//                    map.put(END_TIME_FIELD, i.endTime());
//                    map.put(TYPE_FIELD, i.typeAsString());
//                    map.put(CONTENT_FIELD, i.content());
//
//                    return map;
//                })
//                .collect(Collectors.toList());
//    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }
}
