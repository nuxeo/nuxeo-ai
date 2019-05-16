/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.enrichment;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.functions.AbstractSuggestionConsumer;
import org.nuxeo.ai.metadata.SuggestionMetadata;

/**
 * Example consumer of suggestion data
 */
public class CustomSuggestionConsumer extends AbstractSuggestionConsumer {

    private static final Log log = LogFactory.getLog(CustomSuggestionConsumer.class);

    @Override
    public void accept(SuggestionMetadata suggestionMetadata) {
        log.info("Metadata is "+ suggestionMetadata);
    }
}
