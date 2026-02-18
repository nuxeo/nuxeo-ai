/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.ai.aws.mapper;

import org.nuxeo.ai.aws.dto.TranscriptionJobResult;

import software.amazon.awssdk.services.transcribe.model.GetTranscriptionJobResponse;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobResponse;

/**
 * Mapper utility class for converting AWS Transcribe SDK model objects to domain DTOs. This class isolates all AWS SDK
 * model dependencies for Transcribe service.
 */
public final class TranscribeMapper {

    private TranscribeMapper() {
        // Utility class - prevent instantiation
    }

    /**
     * Convert AWS SDK StartTranscriptionJobResponse to domain TranscriptionJobResult
     */
    public static TranscriptionJobResult mapStartTranscriptionJobResponse(StartTranscriptionJobResponse awsResponse) {
        if (awsResponse == null || awsResponse.transcriptionJob() == null) {
            return null;
        }

        var awsJob = awsResponse.transcriptionJob();
        return new TranscriptionJobResult(awsJob.transcriptionJobName(), awsJob.transcriptionJobStatusAsString(), null, // URI
                                                                                                                        // not
                                                                                                                        // available
                                                                                                                        // in
                                                                                                                        // start
                                                                                                                        // response
                awsJob.languageCodeAsString(), null // Completion time not available in start response
        );
    }

    /**
     * Convert AWS SDK GetTranscriptionJobResponse to domain TranscriptionJobResult
     */
    public static TranscriptionJobResult mapGetTranscriptionJobResponse(GetTranscriptionJobResponse awsResponse) {
        if (awsResponse == null || awsResponse.transcriptionJob() == null) {
            return null;
        }

        var awsJob = awsResponse.transcriptionJob();
        var transcript = awsJob.transcript();

        return new TranscriptionJobResult(awsJob.transcriptionJobName(), awsJob.transcriptionJobStatusAsString(),
                transcript != null ? transcript.transcriptFileUri() : null, awsJob.languageCodeAsString(),
                awsJob.completionTime() != null ? awsJob.completionTime().toEpochMilli() / 1000.0f : null);
    }
}
