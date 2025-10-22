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
package org.nuxeo.ai.translate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.aws.abstraction.AWSServiceRegistry;
import org.nuxeo.ai.aws.abstraction.TranslateServiceFacade;
import org.nuxeo.ai.aws.abstraction.dto.TranslateRequest;
import org.nuxeo.ai.aws.dto.TranslationResult;
import org.nuxeo.ai.aws.dto.TranslateResult;
import org.nuxeo.ai.metrics.AWSMetrics;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Calls AWS Translate via abstraction layer - NO AWS SDK IMPORTS!
 * All AWS SDK dependencies are completely isolated in the facade layer.
 * This service now only depends on our abstraction DTOs and interfaces.
 */
public class TranslateServiceImpl extends DefaultComponent implements TranslateService {

    private static final Log log = LogFactory.getLog(TranslateServiceImpl.class);

    protected TranslateServiceFacade translateFacade;
    protected AWSMetrics awsMetrics;

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        // Get facade through registry - no AWS SDK dependencies
        AWSServiceRegistry registry = Framework.getService(AWSServiceRegistry.class);
        translateFacade = registry.getTranslateService();
        awsMetrics = Framework.getService(AWSMetrics.class);
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        translateFacade = null;
        awsMetrics = null;
    }

    @Override
    public TranslationResult translateText(String text, String sourceLanguageCode, String targetLanguageCode) {
        if (log.isDebugEnabled()) {
            log.debug("Calling translateText from " + sourceLanguageCode + " to " + targetLanguageCode);
        }

        // Create abstraction DTO instead of AWS SDK request
        TranslateRequest.TranslateText request = new TranslateRequest.TranslateText(
                text, sourceLanguageCode, targetLanguageCode);

        // Call through facade - no AWS SDK objects involved
        TranslateResult result = translateFacade.translateText(request);

        if (log.isDebugEnabled()) {
            log.debug("TranslateResult: " + result);
        }

        if (awsMetrics != null) {
            awsMetrics.updateTranslateCharacterUnits((long) text.length());
        }

        // Convert to existing DTO format for backward compatibility
        return convertToTranslationResult(result);
    }

    /**
     * Helper method to maintain backward compatibility with existing TranslationResult DTO
     */
    private TranslationResult convertToTranslationResult(TranslateResult result) {
        return new TranslationResult(
                result.getTranslatedText(),
                result.getSourceLanguageCode(),
                result.getTargetLanguageCode()
        );
    }
}
