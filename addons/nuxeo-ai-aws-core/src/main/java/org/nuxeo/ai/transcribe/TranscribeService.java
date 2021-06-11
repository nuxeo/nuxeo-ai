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

import java.util.List;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ecm.core.api.Blob;
import com.amazonaws.services.transcribe.AmazonTranscribe;
import com.amazonaws.services.transcribe.model.StartTranscriptionJobResult;
import com.amazonaws.services.transcribe.model.TranscriptionJob;

/**
 * Service interface intended for Video/Audio transcription
 */
public interface TranscribeService {

    /**
     * Start transcription job for
     *
     * @param blob      that contains Video/Audio
     * @param languages an array of languages
     * @return {@link TranscriptionJob} of created request
     */
    StartTranscriptionJobResult requestTranscription(Blob blob, String... languages);

    /**
     * @param transcription to convert to lables
     * @return a {@link List} of {@link AIMetadata.Label}
     */
    List<AIMetadata.Label> asLabels(AudioTranscription transcription);

    /**
     * Unique Job Name
     *
     * @param blob for digest
     * @param code language code
     * @return a UUID
     */
    String getJobName(Blob blob, String code);

    /**
     * Get AWS Transcribe Client
     *
     * @return {@link AmazonTranscribe}
     */
    AmazonTranscribe getClient();
}
