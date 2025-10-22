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
 * AWS SDK-independent request DTOs for Comprehend service.
 * These DTOs isolate service implementations from AWS SDK model classes.
 */
public class ComprehendRequest {

    public static class DetectSentiment {
        private final String text;
        private final String languageCode;

        public DetectSentiment(String text, String languageCode) {
            this.text = text;
            this.languageCode = languageCode;
        }

        public String getText() { return text; }
        public String getLanguageCode() { return languageCode; }
    }

    public static class DetectEntities {
        private final String text;
        private final String languageCode;

        public DetectEntities(String text, String languageCode) {
            this.text = text;
            this.languageCode = languageCode;
        }

        public String getText() { return text; }
        public String getLanguageCode() { return languageCode; }
    }

    public static class DetectKeyPhrases {
        private final String text;
        private final String languageCode;

        public DetectKeyPhrases(String text, String languageCode) {
            this.text = text;
            this.languageCode = languageCode;
        }

        public String getText() { return text; }
        public String getLanguageCode() { return languageCode; }
    }
}
