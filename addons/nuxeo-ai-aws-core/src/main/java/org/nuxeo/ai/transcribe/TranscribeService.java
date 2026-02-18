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

import java.util.ArrayList;
import java.util.List;

import org.nuxeo.ai.aws.dto.TranscriptionJobResult;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ecm.core.api.Blob;

/**
 * Service interface intended for Video/Audio transcription - Now using domain DTOs This interface is completely
 * independent of AWS SDK implementation details
 */
public interface TranscribeService {

    /**
     * Start transcription job for
     *
     * @param blob that contains Video/Audio
     * @param languages an array of languages
     * @return {@link TranscriptionJobResult} of created request
     */
    TranscriptionJobResult requestTranscription(Blob blob, String... languages);

    /**
     * Get transcription job status and results
     *
     * @param jobName the name of the transcription job
     * @return {@link TranscriptionJobResult} with job status and results
     */
    TranscriptionJobResult getTranscriptionJob(String jobName);

    /**
     * Delete transcription job
     *
     * @param jobName the name of the transcription job to delete
     */
    void deleteTranscriptionJob(String jobName);

    /**
     * Process transcription results into AI metadata
     *
     * @param result the transcription job result
     * @return List of AIMetadata extracted from transcription
     */
    List<AIMetadata> processTranscriptionResult(TranscriptionJobResult result);

    /**
     * New helper method used by tests to convert a transcription to labels (migrated from provider)
     */
    default List<AIMetadata.Label> asLabels(AudioTranscription transcription) {
        if (transcription == null || transcription.getResults() == null
                || transcription.getResults().getItems() == null) {
            return java.util.Collections.emptyList();
        }
        List<AIMetadata.Label> labels = new ArrayList<>();
        for (AudioTranscription.Item item : transcription.getResults().getItems()) {
            if (item.getType() == AudioTranscription.Type.PRONUNCIATION && !item.getAlternatives().isEmpty()) {
                double conf = item.getAlternatives().get(0).confidence;
                String content = item.getAlternatives().get(0).content;
                if (org.apache.commons.lang3.StringUtils.isNotBlank(content)) {
                    labels.add(new AIMetadata.Label(content, (float) conf));
                }
            }
        }
        return labels;
    }
}
