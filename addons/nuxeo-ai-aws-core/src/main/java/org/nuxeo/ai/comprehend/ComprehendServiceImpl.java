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
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.metrics.AWSMetrics;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.DetectEntitiesRequest;
import software.amazon.awssdk.services.comprehend.model.DetectEntitiesResponse;
import software.amazon.awssdk.services.comprehend.model.DetectKeyPhrasesRequest;
import software.amazon.awssdk.services.comprehend.model.DetectKeyPhrasesResponse;
import software.amazon.awssdk.services.comprehend.model.DetectSentimentRequest;
import software.amazon.awssdk.services.comprehend.model.DetectSentimentResponse;

/**
 * Calls AWS Comprehend apis
 */
public class ComprehendServiceImpl extends DefaultComponent implements ComprehendService {

    private static final Log log = LogFactory.getLog(ComprehendServiceImpl.class);

    protected volatile ComprehendClient client;

    protected AWSMetrics awsMetrics;

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        awsMetrics = Framework.getService(AWSMetrics.class);
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        client = null;
    }

    @Override
    public DetectSentimentResponse detectSentiment(String text, String languageCode) {
        if (log.isDebugEnabled()) {
            log.debug("Calling DetectSentiment for " + text);
        }

        DetectSentimentRequest request = DetectSentimentRequest.builder()
                .text(text)
                .languageCode(languageCode)
                .build();

        DetectSentimentResponse result = getClient().detectSentiment(request);
        if (log.isDebugEnabled()) {
            log.debug("DetectSentimentResult is " + result);
        }
        awsMetrics.updateComprehendSentimentUnits(text.length() / 100L);
        return result;
    }

    @Override
    public DetectKeyPhrasesResponse detectKeyPhrases(String text, String languageCode) {
        if (log.isDebugEnabled()) {
            log.debug("Calling DetectKeyPhrases for " + text);
        }

        DetectKeyPhrasesRequest request = DetectKeyPhrasesRequest.builder()
                .text(text)
                .languageCode(languageCode)
                .build();

        DetectKeyPhrasesResponse result = getClient().detectKeyPhrases(request);
        if (log.isDebugEnabled()) {
            log.debug("DetectKeyPhrasesResult is " + result);
        }
        awsMetrics.updateComprehendKeyphraseUnits(text.length() / 100L);
        return result;
    }

    @Override
    public DetectEntitiesResponse detectEntities(String text, String languageCode) {
        if (log.isDebugEnabled()) {
            log.debug("Calling DetectEntities for " + text);
        }

        DetectEntitiesRequest request = DetectEntitiesRequest.builder()
                .text(text)
                .languageCode(languageCode)
                .build();
        DetectEntitiesResponse result = getClient().detectEntities(request);
        if (log.isDebugEnabled()) {
            log.debug("DetectEntitiesResult is " + result);
        }
        awsMetrics.updateComprehendEntitiesUnits(text.length() / 100L);
        return result;
    }

    protected ComprehendClient getClient() {
        ComprehendClient result = client;
        if (result == null) {
            synchronized (this) {
                result = client;
                if (result == null) {
                    client = result = ComprehendClient.builder()
                            .region(AWSHelper.getInstance().getRegion())
                            .credentialsProvider(AWSHelper.getInstance().getCredentialsProvider())
                            .build();
                }
            }
        }
        return result;
    }
}
