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

import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.services.DocMetadataService;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.validation.DocumentValidationException;
import org.nuxeo.ecm.core.api.validation.ValidationViolation;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Saves enrichment metadata
 */
public class SaveEnrichmentFunction extends AbstractEnrichmentConsumer {

    private static final Logger log = LogManager.getLogger(SaveEnrichmentFunction.class);

    @Override
    public void accept(EnrichmentMetadata metadata) {
        TransactionHelper.runInTransaction(() -> CoreInstance.doPrivileged(metadata.context.repositoryName, session -> {
            DocMetadataService docMetadataService = Framework.getService(DocMetadataService.class);

            log.debug("Saving enrichment for document {}.", metadata.context.documentRef);
            DocumentModel doc = docMetadataService.saveEnrichment(session, metadata);

            if (doc == null) {
                log.warn("Failed to save enrichment for document {}.", metadata.context.documentRef);
                return null;
            }

            log.debug("Checking if the document is checked out and if a base version exists for the document {}.",
                    doc.getId());

            DocumentRef baseVersionRef = session.getBaseVersion(doc.getRef());

            if (baseVersionRef == null && !doc.isCheckedOut()) {
                log.error("Failed to save enrichment for document {}. The document is corrupt and requires a "
                                + "manual intervention to be fixed. The document is not checked out and no base version was found.",
                        doc.getId());
                return null;
            }

            if (doc.isImmutable()) {
                log.error("Attempt to write into an Immutable Document Model id: {}, AI Model name {}", doc.getId(),
                        metadata.getModelName());
                return null;
            }

            try {
                log.debug("Saving enrichment for document {}.", doc.getId());
                session.saveDocument(doc);
                log.debug("Enrichment for document {} was successfully saved.", doc.getId());
            } catch (DocumentValidationException e) {
                log.warn("Failed to save document enrichment data for {}; error: {}", metadata.context.documentRef,
                        e.getMessage());
                if (log.isDebugEnabled()) {
                    // log field violations
                    List<ValidationViolation> violations = e.getReport().asList();
                    for (ValidationViolation violation : violations) {
                        log.debug("Violation message: {}, Violation message key: {}",
                                violation.getMessage(new Locale("en")), violation.getMessageKey());
                    }
                }
            } catch (Exception e) {
                log.error("An unexpected exception occurred saving enrichment for document with id {}; error: {}",
                        doc.getId(), e.getMessage());
                throw e;
            }

            return null;
        }));
    }
}
