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
 * AWS SDK-independent request DTOs for Translate service.
 */
public class TranslateRequest {

    public static class TranslateText {
        private final String text;
        private final String sourceLanguageCode;
        private final String targetLanguageCode;

        public TranslateText(String text, String sourceLanguageCode, String targetLanguageCode) {
            this.text = text;
            this.sourceLanguageCode = sourceLanguageCode;
            this.targetLanguageCode = targetLanguageCode;
        }

        public String getText() { return text; }
        public String getSourceLanguageCode() { return sourceLanguageCode; }
        public String getTargetLanguageCode() { return targetLanguageCode; }
    }
}
