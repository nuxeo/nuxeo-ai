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

import com.amazonaws.services.transcribe.AmazonTranscribe;
import com.amazonaws.services.transcribe.AmazonTranscribeClientBuilder;
import com.amazonaws.services.transcribe.model.ConflictException;
import com.amazonaws.services.transcribe.model.DeleteTranscriptionJobRequest;
import com.amazonaws.services.transcribe.model.Media;
import com.amazonaws.services.transcribe.model.MediaFormat;
import com.amazonaws.services.transcribe.model.StartTranscriptionJobRequest;
import com.amazonaws.services.transcribe.model.StartTranscriptionJobResult;
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
import org.nuxeo.ecm.core.storage.sql.S3BinaryManager;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;

import static org.nuxeo.ai.transcribe.AudioTranscription.Type.PRONUNCIATION;

public class TranscribeServiceImpl implements TranscribeService {

    public static final String AUTOMATIC_LANG = "automatic";

    private static final Logger log = LogManager.getLogger(TranscribeServiceImpl.class);

    private static final int DEFAULT_HZ = 16_000;

    protected AmazonTranscribe client;

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

        if ((provider instanceof S3BinaryManager) || (provider instanceof S3BlobProvider)) {

            String bucket;
            String prefix;

            if (provider instanceof S3BinaryManager) {
                S3BinaryManager s3bm = (S3BinaryManager) provider;
                bucket = s3bm.getBucketName();
                prefix = s3bm.getBucketPrefix();
            } else {
                S3BlobProvider s3bp = (S3BlobProvider) provider;
                bucket = s3bp.config.bucketName;
                prefix = s3bp.config.bucketPrefix;
            }

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
    public StartTranscriptionJobResult requestTranscription(Blob blob, String ...languages) {
        URI blobURI = getBlobURI(blob, false);
        Media media = new Media().withMediaFileUri(blobURI.toString());
        StartTranscriptionJobRequest request = new StartTranscriptionJobRequest().withIdentifyLanguage(true)
                                                                                 .withMedia(media)
                                                                                 .withTranscriptionJobName(
                                                                                         getJobName(blob,
                                                                                                 AUTOMATIC_LANG))
                                                                                 .withMediaFormat(MediaFormat.Wav)
                                                                                 .withMediaSampleRateHertz(DEFAULT_HZ);

        if (StringUtils.isNoneBlank(languages)) {
            request = request.withLanguageOptions(languages);
        }

        StartTranscriptionJobResult result;
        try {
            result = getClient().startTranscriptionJob(request);
            Framework.getService(AWSMetrics.class).getTranscribeGlobalCalls().inc();
        } catch (ConflictException e) {
            String jobName = getJobName(blob, AUTOMATIC_LANG);
            log.error("Job already exist {}; Deleting it", jobName);
            DeleteTranscriptionJobRequest deleteReq = new DeleteTranscriptionJobRequest().withTranscriptionJobName(
                    jobName).withSdkClientExecutionTimeout(5000);
            getClient().deleteTranscriptionJob(deleteReq);

            result = getClient().startTranscriptionJob(request);
        }

        return result;
    }

    @Override
    public List<AIMetadata.Label> asLabels(AudioTranscription transcription) {
        return transcription.results.items.stream()
                                          .filter(item -> PRONUNCIATION.realName().equals(item.type))
                                          .map(item -> new AIMetadata.Label(item.getContent(), 0.f,
                                                  (long) (Float.parseFloat(item.startTime)) * 1000))
                                          .collect(Collectors.toList());
    }

    @Override
    public String getJobName(Blob blob, String code) {
        return code + "_" + blob.getDigest();
    }

    @Override
    public AmazonTranscribe getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AmazonTranscribeClientBuilder.standard()
                                                          .withCredentials(
                                                                  AWSHelper.getInstance().getCredentialsProvider())
                                                          .withRegion(AWSHelper.getInstance().getRegion())
                                                          .build();
                }
            }
        }

        return client;
    }
}
