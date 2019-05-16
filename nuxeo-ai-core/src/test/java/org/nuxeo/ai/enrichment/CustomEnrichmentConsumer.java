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

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.functions.AbstractEnrichmentConsumer;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Example consumer of enrichment data
 */
public class CustomEnrichmentConsumer extends AbstractEnrichmentConsumer {

    private static final Log log = LogFactory.getLog(CustomEnrichmentConsumer.class);

    @Override
    public void accept(EnrichmentMetadata metadata) {
        TransactionHelper.runInTransaction(
                () -> CoreInstance.doPrivileged(metadata.context.repositoryName, session -> {
                    try {
                        // DocumentModel doc = session.getDocument(new IdRef(metadata.context.documentRef));
                        metadata.getTags().forEach(tag -> log.debug("A tag " + tag));
                        metadata.getLabels().forEach(label -> log.debug("A label " + label));
                        String raw = EnrichmentUtils.getRawBlob(metadata);
                        log.debug("Raw is " + raw);
                        // session.saveDocument(doc);
                    } catch (DocumentNotFoundException e) {
                        log.info("Missing doc " + metadata.context.documentRef);
                    } catch (IOException e) {
                        log.warn("Enrichment error ", e);
                    }
                })
        );
    }
}
