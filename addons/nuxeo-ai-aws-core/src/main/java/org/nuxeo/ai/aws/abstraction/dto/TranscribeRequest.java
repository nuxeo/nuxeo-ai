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
@SuppressWarnings("java:S100") // Records are types and follow PascalCase, not camelCase
public class TranscribeRequest {

    private TranscribeRequest() {
        // Utility class, hide constructor
    }

    public static record StartTranscription(String jobName, String mediaFileUri, String mediaFormat,
            String languageCode, String outputBucketName, boolean enableSpeakerLabels, int maxSpeakerLabels) {
        // Record auto-generates constructor, accessors, equals, hashCode, and toString
    }

    public static record GetTranscriptionJob(String jobName) {
        // Record auto-generates constructor, accessors, equals, hashCode, and toString
    }

    public static record DeleteTranscriptionJob(String jobName) {
        // Record auto-generates constructor, accessors, equals, hashCode, and toString
    }
}
