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
package org.nuxeo.ai.translate;

import org.nuxeo.ai.aws.dto.TranslationResult;

/**
 * Works with AWS Translate.
 * <p>
 * Since 5.0.0, this interface uses domain DTOs ({@link TranslationResult}) instead of AWS SDK models, making it
 * completely independent of AWS SDK implementation details. This is a breaking API change from previous versions where
 * the return type was the AWS SDK {@code TranslateTextResult}.
 *
 * @since 2.0
 */
public interface TranslateService {

    /**
     * Translates text from source to target language.
     *
     * @param text the text to translate
     * @param sourceLanguageCode the source language code (e.g. "en")
     * @param targetLanguageCode the target language code (e.g. "fr")
     * @return translation result containing translated text and language codes
     * @since 5.0.0
     */
    TranslationResult translateText(String text, String sourceLanguageCode, String targetLanguageCode);
}
