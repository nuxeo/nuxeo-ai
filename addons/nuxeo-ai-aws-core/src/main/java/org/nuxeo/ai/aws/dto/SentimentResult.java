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
public record SentimentResult(String sentiment, float positive, float negative, float neutral, float mixed) {

    // This method is meaningful, so we keep it
    public SentimentScore getScores() {
        return new SentimentScore(positive, negative, neutral, mixed);
    }

    public static record SentimentScore(float positive, float negative, float neutral, float mixed) {
    }
}
