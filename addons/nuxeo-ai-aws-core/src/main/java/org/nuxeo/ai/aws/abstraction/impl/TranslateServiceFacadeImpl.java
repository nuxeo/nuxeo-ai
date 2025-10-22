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
import org.nuxeo.ai.aws.abstraction.TranslateServiceFacade;
import org.nuxeo.ai.aws.abstraction.dto.TranslateRequest;
import org.nuxeo.ai.aws.dto.TranslateResult;
import org.nuxeo.ai.aws.mapper.TranslateMapper;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;

import software.amazon.awssdk.services.translate.TranslateClient;
import software.amazon.awssdk.services.translate.model.TranslateTextRequest;

/**
 * Implementation of TranslateServiceFacade that isolates ALL AWS SDK dependencies.
 * This is the ONLY class that imports AWS SDK classes for Translate operations.
 */
public class TranslateServiceFacadeImpl extends DefaultComponent implements TranslateServiceFacade {

    private static final Log log = LogFactory.getLog(TranslateServiceFacadeImpl.class);

    @Override
    public TranslateResult translateText(TranslateRequest.TranslateText request) {
        try {
            AWSClientFactory clientFactory = Framework.getService(AWSClientFactory.class);
            TranslateClient client = clientFactory.getTranslateClient();

            TranslateTextRequest awsRequest = TranslateTextRequest.builder()
                    .text(request.getText())
                    .sourceLanguageCode(request.getSourceLanguageCode())
                    .targetLanguageCode(request.getTargetLanguageCode())
                    .build();

            var response = client.translateText(awsRequest);
            return TranslateMapper.mapToTranslateResult(response);
        } catch (Exception e) {
            log.error("Error translating text", e);
            throw new RuntimeException("Failed to translate text", e);
        }
    }
}
