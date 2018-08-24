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
import org.nuxeo.aws.credentials.NuxeoCredentialsProviderChain;
import org.nuxeo.aws.credentials.NuxeoRegionProviderChain;

import com.amazonaws.services.comprehend.AmazonComprehend;
import com.amazonaws.services.comprehend.AmazonComprehendClientBuilder;
import com.amazonaws.services.comprehend.model.DetectSentimentRequest;
import com.amazonaws.services.comprehend.model.DetectSentimentResult;

/**
 * Calls AWS Comprehend apis
 */
public class ComprehendServiceImpl implements ComprehendService {

    private static final Log log = LogFactory.getLog(ComprehendServiceImpl.class);

    protected volatile AmazonComprehend client;

    /**
     * Get the AmazonComprehend client
     */
    protected AmazonComprehend getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    AmazonComprehendClientBuilder builder =
                            AmazonComprehendClientBuilder.standard()
                                                         .withCredentials(NuxeoCredentialsProviderChain.getInstance())
                                                         .withRegion(NuxeoRegionProviderChain.getInstance()
                                                                                             .getRegion());
                    client = builder.build();
                }
            }
        }
        return client;
    }

    @Override
    public DetectSentimentResult detectSentiment(String text, String languageCode) {

        if (log.isDebugEnabled()) {
            log.debug("Calling DetectSentiment for " + text);
        }

        DetectSentimentRequest detectSentimentRequest = new DetectSentimentRequest().withText(text)
                                                                                    .withLanguageCode(languageCode);
        DetectSentimentResult detectSentimentResult = getClient().detectSentiment(detectSentimentRequest);

        if (log.isDebugEnabled()) {
            log.debug("DetectSentimentResult is " + detectSentimentResult);
        }
        return detectSentimentResult;
    }
}
