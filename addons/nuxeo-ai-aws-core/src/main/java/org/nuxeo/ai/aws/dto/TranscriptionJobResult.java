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
public class TranscriptionJobResult {

    private final String jobName;
    private final String jobStatus;
    private final String transcriptFileUri;
    private final String languageCode;
    private final Float completionTime;

    public TranscriptionJobResult(String jobName, String jobStatus, String transcriptFileUri, String languageCode) {
        this(jobName, jobStatus, transcriptFileUri, languageCode, null);
    }

    public TranscriptionJobResult(String jobName, String jobStatus, String transcriptFileUri, String languageCode, Float completionTime) {
        this.jobName = jobName;
        this.jobStatus = jobStatus;
        this.transcriptFileUri = transcriptFileUri;
        this.languageCode = languageCode;
        this.completionTime = completionTime;
    }

    public String getJobName() { return jobName; }
    public String getJobStatus() { return jobStatus; }
    public String getTranscriptFileUri() { return transcriptFileUri; }
    public String getLanguageCode() { return languageCode; }
    public Float getCompletionTime() { return completionTime; }

    @Override
    public String toString() {
        return "TranscriptionJobResult{" +
                "jobName='" + jobName + '\'' +
                ", jobStatus='" + jobStatus + '\'' +
                ", languageCode='" + languageCode + '\'' +
                '}';
    }
}
