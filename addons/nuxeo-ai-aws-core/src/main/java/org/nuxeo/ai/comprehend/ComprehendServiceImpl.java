/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Gethin James
 */
package org.nuxeo.ai.comprehend;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.aws.abstraction.AWSServiceRegistry;
import org.nuxeo.ai.aws.abstraction.ComprehendServiceFacade;
import org.nuxeo.ai.aws.abstraction.dto.ComprehendRequest;
import org.nuxeo.ai.aws.dto.EntitiesResult;
import org.nuxeo.ai.aws.dto.KeyPhrasesResult;
import org.nuxeo.ai.aws.dto.SentimentResult;
import org.nuxeo.ai.metrics.AWSMetrics;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Calls AWS Comprehend APIs via abstraction layer - NO AWS SDK IMPORTS! All AWS SDK dependencies are completely
 * isolated in the facade layer. This service now only depends on our abstraction DTOs and interfaces.
 */
public class ComprehendServiceImpl extends DefaultComponent implements ComprehendService {

    private static final Log log = LogFactory.getLog(ComprehendServiceImpl.class);

    protected ComprehendServiceFacade comprehendFacade;

    protected AWSMetrics awsMetrics;

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        // Get facade through registry - no AWS SDK dependencies
        AWSServiceRegistry registry = Framework.getService(AWSServiceRegistry.class);
        comprehendFacade = registry.getComprehendService();
        awsMetrics = Framework.getService(AWSMetrics.class);
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        comprehendFacade = null;
        awsMetrics = null;
    }

    @Override
    public SentimentResult detectSentiment(String text, String languageCode) {
        if (log.isDebugEnabled()) {
            log.debug("Calling DetectSentiment for " + text);
        }

        // Create abstraction DTO instead of AWS SDK request
        ComprehendRequest.DetectSentiment request = new ComprehendRequest.DetectSentiment(text, languageCode);

        // Call through facade - no AWS SDK objects involved
        SentimentResult result = comprehendFacade.detectSentiment(request);

        if (log.isDebugEnabled()) {
            log.debug("DetectSentimentResult is " + result);
        }

        if (awsMetrics != null) {
            awsMetrics.updateComprehendSentimentUnits(text.length() / 100L);
        }

        return result;
    }

    @Override
    public KeyPhrasesResult detectKeyPhrases(String text, String languageCode) {
        if (log.isDebugEnabled()) {
            log.debug("Calling DetectKeyPhrases for " + text);
        }

        // Create abstraction DTO instead of AWS SDK request
        ComprehendRequest.DetectKeyPhrases request = new ComprehendRequest.DetectKeyPhrases(text, languageCode);

        // Call through facade - no AWS SDK objects involved
        KeyPhrasesResult result = comprehendFacade.detectKeyPhrases(request);

        if (log.isDebugEnabled()) {
            log.debug("DetectKeyPhrasesResult is " + result);
        }

        if (awsMetrics != null) {
            awsMetrics.updateComprehendKeyphraseUnits(text.length() / 100L);
        }

        return result;
    }

    @Override
    public EntitiesResult detectEntities(String text, String languageCode) {
        if (log.isDebugEnabled()) {
            log.debug("Calling DetectEntities for " + text);
        }

        // Create abstraction DTO instead of AWS SDK request
        ComprehendRequest.DetectEntities request = new ComprehendRequest.DetectEntities(text, languageCode);

        // Call through facade - no AWS SDK objects involved
        EntitiesResult result = comprehendFacade.detectEntities(request);

        if (log.isDebugEnabled()) {
            log.debug("DetectEntitiesResult is " + result);
        }

        if (awsMetrics != null) {
            awsMetrics.updateComprehendEntitiesUnits(text.length() / 100L);
        }

        return result;
    }
}
