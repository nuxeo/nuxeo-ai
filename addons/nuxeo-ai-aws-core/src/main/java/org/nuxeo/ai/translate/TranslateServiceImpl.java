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
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.metrics.AWSMetrics;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import com.amazonaws.services.translate.AmazonTranslate;
import com.amazonaws.services.translate.AmazonTranslateClientBuilder;
import com.amazonaws.services.translate.model.TranslateTextRequest;
import com.amazonaws.services.translate.model.TranslateTextResult;

/**
 * Calls AWS translate
 */
public class TranslateServiceImpl extends DefaultComponent implements TranslateService {

    private static final Log log = LogFactory.getLog(TranslateServiceImpl.class);

    protected volatile AmazonTranslate client;

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

    protected AmazonTranslate getClient() {
        AmazonTranslate localClient = client;
        if (localClient == null) {
            synchronized (this) {
                localClient = client;
                if (localClient == null) {
                    AmazonTranslateClientBuilder builder = AmazonTranslateClientBuilder.standard()
                                                                                       .withCredentials(
                                                                                               AWSHelper.getInstance()
                                                                                                        .getCredentialsProvider())
                                                                                       .withRegion(
                                                                                               AWSHelper.getInstance()
                                                                                                        .getRegion());
                    client = localClient = builder.build();
                }
            }
        }
        return localClient;
    }

    @Override
    public TranslateTextResult translateText(String text, String sourceLanguageCode, String targetLanguageCode) {
        if (log.isDebugEnabled()) {
            log.debug("Calling Translate for " + text);
        }
        TranslateTextRequest request = new TranslateTextRequest().withText(text)
                                                                 .withSourceLanguageCode(sourceLanguageCode)
                                                                 .withTargetLanguageCode(targetLanguageCode);
        awsMetrics.getTranslateTotalChars().update(text.length());
        return getClient().translateText(request);
    }
}
