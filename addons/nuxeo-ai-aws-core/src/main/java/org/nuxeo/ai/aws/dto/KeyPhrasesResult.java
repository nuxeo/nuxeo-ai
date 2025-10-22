/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.aws.dto;

import java.util.List;

/**
 * AWS SDK-independent result DTO for key phrase extraction operations.
 */
public class KeyPhrasesResult {

    private final List<KeyPhrase> keyPhrases;

    public KeyPhrasesResult(List<KeyPhrase> keyPhrases) {
        this.keyPhrases = keyPhrases;
    }

    public List<KeyPhrase> getKeyPhrases() { return keyPhrases; }

    public static class KeyPhrase {
        private final String text;
        private final float score;
        private final int beginOffset;
        private final int endOffset;

        public KeyPhrase(String text, float score, int beginOffset, int endOffset) {
            this.text = text;
            this.score = score;
            this.beginOffset = beginOffset;
            this.endOffset = endOffset;
        }

        public String getText() { return text; }
        public float getScore() { return score; }
        public int getBeginOffset() { return beginOffset; }
        public int getEndOffset() { return endOffset; }

        @Override
        public String toString() {
            return "KeyPhrase{" +
                    "text='" + text + '\'' +
                    ", score=" + score +
                    ", beginOffset=" + beginOffset +
                    ", endOffset=" + endOffset +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "KeyPhrasesResult{" +
                "keyPhrases=" + keyPhrases +
                '}';
    }
}
