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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metrics.AWSMetrics;
import org.nuxeo.ecm.blob.s3.S3BlobProvider;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.ConflictException;
import software.amazon.awssdk.services.transcribe.model.DeleteTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.GetTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.GetTranscriptionJobResponse;
import software.amazon.awssdk.services.transcribe.model.JobExecutionSettings;
import software.amazon.awssdk.services.transcribe.model.LanguageCode;
import software.amazon.awssdk.services.transcribe.model.Media;
import software.amazon.awssdk.services.transcribe.model.MediaFormat;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobResponse;
import software.amazon.awssdk.services.transcribe.model.TranscriptionJob;

public class TranscribeServiceImpl implements TranscribeService {

    public static final String AUTOMATIC_LANG = "automatic";

    private static final Logger log = LogManager.getLogger(TranscribeServiceImpl.class);

    private static final int DEFAULT_HZ = 16_000;

    protected TranscribeClient client;

    public static URI getBlobURI(Blob blob, boolean signed) throws NuxeoException {
        BlobManager bm = Framework.getService(BlobManager.class);
        BlobProvider provider = bm.getBlobProvider(blob);

        URI uri;
        if (signed) {
            try {
                // generate the signed url
                uri = bm.getURI(blob, BlobManager.UsageHint.DOWNLOAD, null);
                if (uri != null) {
                    return uri;
                }
            } catch (IOException e) {
                log.error("Cannot get a signed URL from the  BinaryManager", e);
            }
        }

        if (blob == null) {
            throw new NuxeoException("Cannot set URI: provided Blob is null");
        }

        if (provider instanceof S3BlobProvider) {

            String bucket;
            String prefix;

            S3BlobProvider s3bp = (S3BlobProvider) provider;
            bucket = s3bp.config.bucketName;
            prefix = s3bp.config.bucketPrefix;

            try {
                return new URI("s3://" + bucket + "/" + StringUtils.defaultString(prefix) + blob.getDigest());
            } catch (URISyntaxException e) {
                throw new NuxeoException(e);
            }
        }

        try {
            if (Framework.isTestModeSet() && blob.getFilename() != null && blob.getFilename().contains("s3")) {
                return new URI(blob.getFilename());
            } else {
                return new URI("blob://" + blob.getDigest());
            }
        } catch (URISyntaxException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public StartTranscriptionJobResponse requestTranscription(Blob blob, String... languages) {
        URI blobURI = getBlobURI(blob, false);
        Media media = Media.builder().mediaFileUri(blobURI.toString()).build();
        StartTranscriptionJobRequest.Builder requestBuilder = StartTranscriptionJobRequest.builder()
                                                                                         .identifyLanguage(true)
                                                                                         .media(media)
                                                                                         .transcriptionJobName(getJobName(blob, AUTOMATIC_LANG))
                                                                                         .mediaFormat(MediaFormat.WAV)
                                                                                         .mediaSampleRateHertz(DEFAULT_HZ);

        if (StringUtils.isNoneBlank(languages)) {
            requestBuilder.languageOptions(Arrays.stream(languages)
                .map(LanguageCode::valueOf)
                .collect(Collectors.toList()));
        }

        StartTranscriptionJobRequest request = requestBuilder.build();

        StartTranscriptionJobResponse result;
        try {
            result = getClient().startTranscriptionJob(request);
            Framework.getService(AWSMetrics.class).getTranscribeGlobalCalls().inc();
        } catch (ConflictException e) {
            String jobName = getJobName(blob, AUTOMATIC_LANG);
            log.error("Job already exist {}; Deleting it", jobName);
            DeleteTranscriptionJobRequest deleteReq = DeleteTranscriptionJobRequest.builder()
                                                                                 .transcriptionJobName(jobName)
                                                                                 .build();
            getClient().deleteTranscriptionJob(deleteReq);

            result = getClient().startTranscriptionJob(request);
        }

        return result;
    }

    @Override
    public List<AIMetadata.Label> asLabels(AudioTranscription transcription) {
        return transcription.results.items.stream()
                                          .filter(item -> "pronunciation".equals(item.type))
                                          .map(item -> new AIMetadata.Label(item.getContent(), 0.f,
                                                  (long) (Float.parseFloat(item.startTime)) * 1000))
                                          .collect(Collectors.toList());
    }

    @Override
    public String getJobName(Blob blob, String code) {
        return code + "_" + blob.getDigest();
    }

    public TranscribeClient getClient() {
        if (client != null) {
            return client;
        }

        synchronized (this) {
            client = TranscribeClient.builder()
                                   .credentialsProvider(AWSHelper.getInstance().getCredentialsProvider())
                                   .region(AWSHelper.getInstance().getRegion())
                                   .build();
            return client;
        }
    }
}
