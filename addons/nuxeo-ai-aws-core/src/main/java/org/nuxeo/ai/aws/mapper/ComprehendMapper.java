/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.aws.mapper;

import org.nuxeo.ai.aws.dto.SentimentResult;
import org.nuxeo.ai.aws.dto.EntitiesResult;
import org.nuxeo.ai.aws.dto.KeyPhrasesResult;
import software.amazon.awssdk.services.comprehend.model.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps AWS SDK Comprehend responses to our abstraction layer DTOs.
 * This is one of the few classes that imports AWS SDK classes.
 */
public class ComprehendMapper {

    public static SentimentResult mapToSentimentResult(DetectSentimentResponse response) {
        return new SentimentResult(
            response.sentiment().toString(),
            response.sentimentScore().positive(),
            response.sentimentScore().negative(),
            response.sentimentScore().neutral(),
            response.sentimentScore().mixed()
        );
    }

    public static EntitiesResult mapToEntitiesResult(DetectEntitiesResponse response) {
        List<EntitiesResult.Entity> entities = response.entities().stream()
            .map(ComprehendMapper::mapToEntity)
            .collect(Collectors.toList());

        return new EntitiesResult(entities);
    }

    private static EntitiesResult.Entity mapToEntity(Entity awsEntity) {
        return new EntitiesResult.Entity(
            awsEntity.text(),
            awsEntity.type().toString(),
            awsEntity.score(),
            awsEntity.beginOffset(),
            awsEntity.endOffset()
        );
    }

    public static KeyPhrasesResult mapToKeyPhrasesResult(DetectKeyPhrasesResponse response) {
        List<KeyPhrasesResult.KeyPhrase> keyPhrases = response.keyPhrases().stream()
            .map(ComprehendMapper::mapToKeyPhrase)
            .collect(Collectors.toList());

        return new KeyPhrasesResult(keyPhrases);
    }

    private static KeyPhrasesResult.KeyPhrase mapToKeyPhrase(software.amazon.awssdk.services.comprehend.model.KeyPhrase awsKeyPhrase) {
        return new KeyPhrasesResult.KeyPhrase(
            awsKeyPhrase.text(),
            awsKeyPhrase.score(),
            awsKeyPhrase.beginOffset(),
            awsKeyPhrase.endOffset()
        );
    }
}
