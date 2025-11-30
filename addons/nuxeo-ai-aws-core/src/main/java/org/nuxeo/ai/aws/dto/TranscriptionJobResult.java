/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * at
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.aws.dto;

/**
 * AWS SDK-independent result DTO for transcription job operations.
 */
public record TranscriptionJobResult(String jobName, String jobStatus, String transcriptFileUri, String languageCode,
        Float completionTime) {
    public TranscriptionJobResult(String jobName, String jobStatus, String transcriptFileUri, String languageCode) {
        this(jobName, jobStatus, transcriptFileUri, languageCode, null);
    }
}
