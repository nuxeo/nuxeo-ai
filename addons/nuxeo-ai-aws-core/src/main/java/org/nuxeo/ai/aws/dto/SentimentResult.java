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

/**
 * AWS SDK-independent result DTO for sentiment analysis operations.
 */
public class SentimentResult {

    private final String sentiment;
    private final float positive;
    private final float negative;
    private final float neutral;
    private final float mixed;

    public SentimentResult(String sentiment, float positive, float negative, float neutral, float mixed) {
        this.sentiment = sentiment;
        this.positive = positive;
        this.negative = negative;
        this.neutral = neutral;
        this.mixed = mixed;
    }

    public String getSentiment() { return sentiment; }
    public float getPositive() { return positive; }
    public float getNegative() { return negative; }
    public float getNeutral() { return neutral; }
    public float getMixed() { return mixed; }

    public SentimentScore getScores() {
        return new SentimentScore(positive, negative, neutral, mixed);
    }

    @Override
    public String toString() {
        return "SentimentResult{" +
                "sentiment='" + sentiment + '\'' +
                ", positive=" + positive +
                ", negative=" + negative +
                ", neutral=" + neutral +
                ", mixed=" + mixed +
                '}';
    }

    public static class SentimentScore {
        private final float positive;
        private final float negative;
        private final float neutral;
        private final float mixed;

        public SentimentScore(float positive, float negative, float neutral, float mixed) {
            this.positive = positive;
            this.negative = negative;
            this.neutral = neutral;
            this.mixed = mixed;
        }

        public float getPositive() { return positive; }
        public float getNegative() { return negative; }
        public float getNeutral() { return neutral; }
        public float getMixed() { return mixed; }
    }
}
