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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.aws.AWSClientFactory;
import org.nuxeo.ai.aws.dto.TranscriptionJobResult;
import org.nuxeo.ai.aws.mapper.TranscribeMapper;
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

import software.amazon.awssdk.services.transcribe.model.ConflictException;
import software.amazon.awssdk.services.transcribe.model.DeleteTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.GetTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.LanguageCode;
import software.amazon.awssdk.services.transcribe.model.Media;
import software.amazon.awssdk.services.transcribe.model.MediaFormat;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobRequest;

/**
 * Implementation of TranscribeService - Now using abstraction layer AWS SDK dependencies are isolated to this
 * implementation class only
 */
public class TranscribeServiceImpl extends DefaultComponent implements TranscribeService {

    public static final String AUTOMATIC_LANG = "automatic";

    private static final Logger log = LogManager.getLogger(TranscribeServiceImpl.class);

    protected AWSClientFactory clientFactory;

    protected AWSMetrics awsMetrics;

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        clientFactory = Framework.getService(AWSClientFactory.class);
        awsMetrics = Framework.getService(AWSMetrics.class);
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        clientFactory = null;
    }

    @Override
    public TranscriptionJobResult requestTranscription(Blob blob, String... languages) {
        if (log.isDebugEnabled()) {
            log.debug("Requesting transcription for blob with languages: " + Arrays.toString(languages));
        }

        try {
            String jobName = generateJobName(blob);
            String mediaUri = getMediaUri(blob);
            MediaFormat mediaFormat = determineMediaFormat(blob);

            StartTranscriptionJobRequest.Builder requestBuilder = StartTranscriptionJobRequest.builder()
                                                                                              .transcriptionJobName(
                                                                                                      jobName)
                                                                                              .media(Media.builder()
                                                                                                          .mediaFileUri(
                                                                                                                  mediaUri)
                                                                                                          .build())
                                                                                              .mediaFormat(mediaFormat);

            // Handle language settings
            if (languages.length == 1 && !AUTOMATIC_LANG.equals(languages[0])) {
                requestBuilder.languageCode(LanguageCode.fromValue(languages[0]));
            } else {
                // Multiple languages or automatic language detection - use language identification
                requestBuilder.identifyLanguage(true);
            }

            var awsResponse = clientFactory.getTranscribeClient().startTranscriptionJob(requestBuilder.build());

            if (log.isDebugEnabled()) {
                log.debug("StartTranscriptionJobResponse: " + awsResponse);
            }

            awsMetrics.updateTranscribeUnits(1L);

            // Convert AWS SDK response to domain DTO
            return TranscribeMapper.mapStartTranscriptionJobResponse(awsResponse);

        } catch (Exception e) {
            if (e instanceof ConflictException) {
                log.warn("Transcription job already exists, attempting to retrieve it");
                // Return existing job info
                return getTranscriptionJob(generateJobName(blob));
            }
            throw new NuxeoException("Failed to start transcription job", e);
        }
    }

    @Override
    public TranscriptionJobResult getTranscriptionJob(String jobName) {
        if (log.isDebugEnabled()) {
            log.debug("Getting transcription job: " + jobName);
        }

        GetTranscriptionJobRequest request = GetTranscriptionJobRequest.builder().transcriptionJobName(jobName).build();

        var awsResponse = clientFactory.getTranscribeClient().getTranscriptionJob(request);

        if (log.isDebugEnabled()) {
            log.debug("GetTranscriptionJobResponse: " + awsResponse);
        }

        // Convert AWS SDK response to domain DTO
        return TranscribeMapper.mapGetTranscriptionJobResponse(awsResponse);
    }

    @Override
    public void deleteTranscriptionJob(String jobName) {
        if (log.isDebugEnabled()) {
            log.debug("Deleting transcription job: " + jobName);
        }

        DeleteTranscriptionJobRequest request = DeleteTranscriptionJobRequest.builder()
                                                                             .transcriptionJobName(jobName)
                                                                             .build();

        clientFactory.getTranscribeClient().deleteTranscriptionJob(request);

        if (log.isDebugEnabled()) {
            log.debug("Deleted transcription job: " + jobName);
        }
    }

    @Override
    public List<AIMetadata> processTranscriptionResult(TranscriptionJobResult result) {
        if (result == null || StringUtils.isEmpty(result.transcriptFileUri())) {
            return Collections.emptyList();
        }

        // This would typically download and parse the transcript JSON file
        // For now, return a placeholder implementation
        log.info("Processing transcription result from: " + result.transcriptFileUri());

        // In a real implementation, you would:
        // 1. Download the transcript file from the URI
        // 2. Parse the JSON content
        // 3. Extract text, timestamps, confidence scores
        // 4. Convert to AIMetadata objects

        return Collections.emptyList();
    }

    /**
     * Generate a unique job name for the transcription
     */
    private String generateJobName(Blob blob) {
        String filename = blob.getFilename();
        if (filename == null) {
            filename = "audio";
        }
        // Remove file extension and non-alphanumeric characters
        String baseName = filename.replaceAll("\\.[^.]+$", "").replaceAll("[^a-zA-Z0-9]", "_");
        return "transcribe_" + baseName + "_" + System.currentTimeMillis();
    }

    /**
     * Get the S3 URI for the media file
     */
    private String getMediaUri(Blob blob) throws NuxeoException {
        if (!(blob instanceof ManagedBlob)) {
            throw new NuxeoException("Blob must be a ManagedBlob for transcription");
        }

        ManagedBlob managedBlob = (ManagedBlob) blob;
        BlobManager blobManager = Framework.getService(BlobManager.class);
        BlobProvider provider = blobManager.getBlobProvider(managedBlob.getProviderId());

        if (!(provider instanceof S3BlobProvider)) {
            throw new NuxeoException("Transcription requires S3 blob storage");
        }

        String bucket = AWSHelper.getS3BucketName();
        return "s3://" + bucket + "/" + managedBlob.getKey();
    }

    /**
     * Determine media format from blob
     */
    private MediaFormat determineMediaFormat(Blob blob) {
        String mimeType = blob.getMimeType();
        String filename = blob.getFilename();

        if (mimeType != null) {
            if (mimeType.contains("mp3"))
                return MediaFormat.MP3;
            if (mimeType.contains("mp4"))
                return MediaFormat.MP4;
            if (mimeType.contains("wav"))
                return MediaFormat.WAV;
            if (mimeType.contains("flac"))
                return MediaFormat.FLAC;
        }

        if (filename != null) {
            String extension = filename.toLowerCase();
            if (extension.endsWith(".mp3"))
                return MediaFormat.MP3;
            if (extension.endsWith(".mp4"))
                return MediaFormat.MP4;
            if (extension.endsWith(".wav"))
                return MediaFormat.WAV;
            if (extension.endsWith(".flac"))
                return MediaFormat.FLAC;
        }

        // Default to MP4 if we can't determine
        return MediaFormat.MP4;
    }
}
