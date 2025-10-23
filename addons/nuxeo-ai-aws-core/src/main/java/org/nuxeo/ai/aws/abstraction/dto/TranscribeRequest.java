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

/**
 * AWS SDK-independent request DTOs for Transcribe service.
 */
public class TranscribeRequest {

    public static record StartTranscription(String jobName, String mediaFileUri, String mediaFormat,
                                             String languageCode, String outputBucketName,
                                             boolean enableSpeakerLabels, int maxSpeakerLabels) {
        public String getJobName() { return jobName; }
        public String getMediaFileUri() { return mediaFileUri; }
        public String getMediaFormat() { return mediaFormat; }
        public String getLanguageCode() { return languageCode; }
        public String getOutputBucketName() { return outputBucketName; }
        public boolean isEnableSpeakerLabels() { return enableSpeakerLabels; }
        public int getMaxSpeakerLabels() { return maxSpeakerLabels; }
    }

    public static record GetTranscriptionJob(String jobName) {
        public String getJobName() { return jobName; }
    }

    public static record DeleteTranscriptionJob(String jobName) {
        public String getJobName() { return jobName; }
    }
}
