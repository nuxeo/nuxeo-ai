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

import com.amazonaws.services.comprehend.AmazonComprehend;
import com.amazonaws.services.comprehend.AmazonComprehendClientBuilder;
import com.amazonaws.services.comprehend.model.DetectSentimentRequest;
import com.amazonaws.services.comprehend.model.DetectSentimentResult;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.runtime.aws.NuxeoAWSCredentialsProvider;
import org.nuxeo.runtime.aws.NuxeoAWSRegionProvider;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Calls AWS Comprehend apis
 */
public class ComprehendServiceImpl extends DefaultComponent implements ComprehendService {

    private static final Log log = LogFactory.getLog(ComprehendServiceImpl.class);

    protected volatile AmazonComprehend client;

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        client = null;
    }

    /**
     * Get the AmazonComprehend client
     */
    protected AmazonComprehend getClient() {
        AmazonComprehend localClient = client;
        if (localClient == null) {
            synchronized (this) {
                localClient = client;
                if (localClient == null) {
                    AmazonComprehendClientBuilder builder =
                            AmazonComprehendClientBuilder.standard()
                                                         .withCredentials(NuxeoAWSCredentialsProvider.getInstance())
                                                         .withRegion(NuxeoAWSRegionProvider.getInstance().getRegion());
                    client = localClient = builder.build();
                }
            }
        }
        return localClient;
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
