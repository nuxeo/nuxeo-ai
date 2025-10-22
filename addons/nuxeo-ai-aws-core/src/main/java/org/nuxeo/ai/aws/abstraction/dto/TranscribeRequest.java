/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.aws.abstraction.dto;

import java.util.List;

/**
 * AWS SDK-independent request DTOs for Transcribe service.
 */
public class TranscribeRequest {

    public static class StartTranscription {
        private final String jobName;
        private final String mediaFileUri;
        private final String mediaFormat;
        private final String languageCode;
        private final String outputBucketName;
        private final boolean enableSpeakerLabels;
        private final int maxSpeakerLabels;

        public StartTranscription(String jobName, String mediaFileUri, String mediaFormat,
                                String languageCode, String outputBucketName,
                                boolean enableSpeakerLabels, int maxSpeakerLabels) {
            this.jobName = jobName;
            this.mediaFileUri = mediaFileUri;
            this.mediaFormat = mediaFormat;
            this.languageCode = languageCode;
            this.outputBucketName = outputBucketName;
            this.enableSpeakerLabels = enableSpeakerLabels;
            this.maxSpeakerLabels = maxSpeakerLabels;
        }

        public String getJobName() { return jobName; }
        public String getMediaFileUri() { return mediaFileUri; }
        public String getMediaFormat() { return mediaFormat; }
        public String getLanguageCode() { return languageCode; }
        public String getOutputBucketName() { return outputBucketName; }
        public boolean isEnableSpeakerLabels() { return enableSpeakerLabels; }
        public int getMaxSpeakerLabels() { return maxSpeakerLabels; }
    }

    public static class GetTranscriptionJob {
        private final String jobName;

        public GetTranscriptionJob(String jobName) {
            this.jobName = jobName;
        }

        public String getJobName() { return jobName; }
    }

    public static class DeleteTranscriptionJob {
        private final String jobName;

        public DeleteTranscriptionJob(String jobName) {
            this.jobName = jobName;
        }

        public String getJobName() { return jobName; }
    }
}
