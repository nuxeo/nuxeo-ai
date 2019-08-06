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
package org.nuxeo.ai.functions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.services.DocMetadataService;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.validation.DocumentValidationException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Saves enrichment metadata
 */
public class SaveEnrichmentFunction extends AbstractEnrichmentConsumer {

    private static final Logger log = LogManager.getLogger(SaveEnrichmentFunction.class);

    @Override
    public void accept(EnrichmentMetadata metadata) {
        TransactionHelper.runInTransaction(
                () -> CoreInstance.doPrivileged(metadata.context.repositoryName, session -> {
                    DocMetadataService docMetadataService = Framework.getService(DocMetadataService.class);
                    DocumentModel doc = docMetadataService.saveEnrichment(session, metadata);
                    if (doc != null) {
                        try {
                            session.saveDocument(doc);
                        } catch (DocumentValidationException e) {
                            log.warn("Failed to save document enrichment data for {}.", metadata.context.documentRef, e);
                        }
                    } else {
                        log.debug("Failed to save enrichment for document {}.", metadata.context.documentRef);
                    }
                })
        );
    }
}
