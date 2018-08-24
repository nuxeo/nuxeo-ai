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
import org.nuxeo.aws.credentials.NuxeoCredentialsProviderChain;
import org.nuxeo.aws.credentials.NuxeoRegionProviderChain;

import com.amazonaws.services.translate.AmazonTranslate;
import com.amazonaws.services.translate.AmazonTranslateClientBuilder;
import com.amazonaws.services.translate.model.TranslateTextRequest;
import com.amazonaws.services.translate.model.TranslateTextResult;

/**
 * Calls AWS translate
 */
public class TranslateServiceImpl implements TranslateService {

    private static final Log log = LogFactory.getLog(TranslateServiceImpl.class);

    protected volatile AmazonTranslate client;

    /**
     * Get the AmazonTranslate client
     */
    protected AmazonTranslate getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    AmazonTranslateClientBuilder builder =
                            AmazonTranslateClientBuilder.standard()
                                                        .withCredentials(NuxeoCredentialsProviderChain.getInstance())
                                                        .withRegion(NuxeoRegionProviderChain.getInstance().getRegion());
                    client = builder.build();
                }
            }
        }
        return client;
    }

    @Override
    public TranslateTextResult translateText(String text, String sourceLanguageCode, String targetLanguageCode) {
        if (log.isDebugEnabled()) {
            log.debug("Calling Translate for " + text);
        }

        TranslateTextRequest request = new TranslateTextRequest()
                .withText(text)
                .withSourceLanguageCode(sourceLanguageCode)
                .withTargetLanguageCode(targetLanguageCode);
        return getClient().translateText(request);
    }
}
