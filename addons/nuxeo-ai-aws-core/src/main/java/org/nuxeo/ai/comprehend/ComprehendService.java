/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 * Contributors:
 *     Gethin James
 */
package org.nuxeo.ai.comprehend;

import com.amazonaws.services.comprehend.model.DetectEntitiesResult;
import com.amazonaws.services.comprehend.model.DetectKeyPhrasesResult;
import com.amazonaws.services.comprehend.model.DetectSentimentResult;

/**
 * Works with AWS Comprehend
 */
public interface ComprehendService {

    /**
     * Detect sentiment for the provided text
     */
    DetectSentimentResult detectSentiment(String text, String languageCode);

    /**
     * Extract key phrases from the given text
     *
     * @param text         provided for extraction
     * @param languageCode code of the language to use for extraction (ie `en`)
     * @return {@link DetectKeyPhrasesResult} as a response value of the service
     */
    DetectKeyPhrasesResult extractKeyphrase(String text, String languageCode);

    /**
     * Detect Entities from the given text
     *
     * @param text         provided for extraction
     * @param languageCode code of the language to use for extraction (ie `en`)
     * @return {@link DetectKeyPhrasesResult} as a response value of the service
     */
    DetectEntitiesResult detectEntities(String text, String languageCode);
}
