/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.aws.abstraction;

import org.nuxeo.ai.aws.abstraction.dto.TranscribeRequest;
import org.nuxeo.ai.aws.dto.TranscriptionJobResult;

/**
 * AWS SDK-independent facade for Transcribe operations.
 */
public interface TranscribeServiceFacade {

    /**
     * Start transcription job
     */
    TranscriptionJobResult startTranscription(TranscribeRequest.StartTranscription request);

    /**
     * Get transcription job status
     */
    TranscriptionJobResult getTranscriptionJob(TranscribeRequest.GetTranscriptionJob request);

    /**
     * Delete transcription job
     */
    void deleteTranscriptionJob(TranscribeRequest.DeleteTranscriptionJob request);
}
