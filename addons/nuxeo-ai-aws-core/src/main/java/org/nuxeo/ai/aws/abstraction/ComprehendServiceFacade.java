/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.aws.abstraction;

import org.nuxeo.ai.aws.abstraction.dto.ComprehendRequest;
import org.nuxeo.ai.aws.dto.SentimentResult;
import org.nuxeo.ai.aws.dto.EntitiesResult;
import org.nuxeo.ai.aws.dto.KeyPhrasesResult;

/**
 * AWS SDK-independent facade for Comprehend operations.
 * Service implementations use this interface instead of importing AWS SDK classes.
 */
public interface ComprehendServiceFacade {

    /**
     * Detect sentiment in text
     */
    SentimentResult detectSentiment(ComprehendRequest.DetectSentiment request);

    /**
     * Detect entities in text
     */
    EntitiesResult detectEntities(ComprehendRequest.DetectEntities request);

    /**
     * Detect key phrases in text
     */
    KeyPhrasesResult detectKeyPhrases(ComprehendRequest.DetectKeyPhrases request);
}
