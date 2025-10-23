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
package org.nuxeo.ai.aws.dto;

import java.util.Objects;

/**
 * Domain-specific DTO for AWS Translate results.
 */
public record TranslationResult(String translatedText, String sourceLanguageCode, String targetLanguageCode) {
    public String getTranslatedText() { return translatedText; }
    public String getSourceLanguageCode() { return sourceLanguageCode; }
    public String getTargetLanguageCode() { return targetLanguageCode; }
    @Override public String toString() { return "TranslationResult{" + "translatedText='" + translatedText + '\'' + ", sourceLanguageCode='" + sourceLanguageCode + '\'' + ", targetLanguageCode='" + targetLanguageCode + '\'' + '}'; }
}
