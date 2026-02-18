/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.aws.abstraction.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.aws.AWSClientFactory;
import org.nuxeo.ai.aws.abstraction.ComprehendServiceFacade;
import org.nuxeo.ai.aws.abstraction.dto.ComprehendRequest;
import org.nuxeo.ai.aws.dto.EntitiesResult;
import org.nuxeo.ai.aws.dto.KeyPhrasesResult;
import org.nuxeo.ai.aws.dto.SentimentResult;
import org.nuxeo.ai.aws.mapper.ComprehendMapper;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.DetectEntitiesRequest;
import software.amazon.awssdk.services.comprehend.model.DetectKeyPhrasesRequest;
import software.amazon.awssdk.services.comprehend.model.DetectSentimentRequest;

/**
 * Implementation of ComprehendServiceFacade that isolates ALL AWS SDK dependencies. This is the ONLY class that imports
 * AWS SDK classes for Comprehend operations. All service implementations use the facade interface instead.
 */
public class ComprehendServiceFacadeImpl extends DefaultComponent implements ComprehendServiceFacade {

    private static final Log log = LogFactory.getLog(ComprehendServiceFacadeImpl.class);

    @Override
    public SentimentResult detectSentiment(ComprehendRequest.DetectSentiment request) {
        try {
            AWSClientFactory clientFactory = Framework.getService(AWSClientFactory.class);
            ComprehendClient client = clientFactory.getComprehendClient();

            DetectSentimentRequest awsRequest = DetectSentimentRequest.builder()
                                                                      .text(request.text())
                                                                      .languageCode(request.languageCode())
                                                                      .build();

            var response = client.detectSentiment(awsRequest);
            return ComprehendMapper.mapToSentimentResult(response);
        } catch (SdkException e) {
            log.error("Error detecting sentiment", e);
            throw new NuxeoException("Failed to detect sentiment", e);
        }
    }

    @Override
    public EntitiesResult detectEntities(ComprehendRequest.DetectEntities request) {
        try {
            AWSClientFactory clientFactory = Framework.getService(AWSClientFactory.class);
            ComprehendClient client = clientFactory.getComprehendClient();

            DetectEntitiesRequest awsRequest = DetectEntitiesRequest.builder()
                                                                    .text(request.text())
                                                                    .languageCode(request.languageCode())
                                                                    .build();

            var response = client.detectEntities(awsRequest);
            return ComprehendMapper.mapToEntitiesResult(response);
        } catch (SdkException e) {
            log.error("Error detecting entities", e);
            throw new NuxeoException("Failed to detect entities", e);
        }
    }

    @Override
    public KeyPhrasesResult detectKeyPhrases(ComprehendRequest.DetectKeyPhrases request) {
        try {
            AWSClientFactory clientFactory = Framework.getService(AWSClientFactory.class);
            ComprehendClient client = clientFactory.getComprehendClient();

            DetectKeyPhrasesRequest awsRequest = DetectKeyPhrasesRequest.builder()
                                                                        .text(request.text())
                                                                        .languageCode(request.languageCode())
                                                                        .build();

            var response = client.detectKeyPhrases(awsRequest);
            return ComprehendMapper.mapToKeyPhrasesResult(response);
        } catch (SdkException e) {
            log.error("Error detecting key phrases", e);
            throw new NuxeoException("Failed to detect key phrases", e);
        }
    }
}
