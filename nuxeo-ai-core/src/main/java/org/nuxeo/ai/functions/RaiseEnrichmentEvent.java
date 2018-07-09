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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.api.event.DocumentEventCategories;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventProducer;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Raises an event when new enrichment data is added
 */
public class RaiseEnrichmentEvent extends AbstractEnrichmentConsumer {

    public static final String ENRICHMENT_CREATED = "enrichmentMetadataCreated";

    public static final String ENRICHMENT_METADATA = "enrichmentMetadata";

    public static final String CATEGORY = "category";

    private static final Log log = LogFactory.getLog(RaiseEnrichmentEvent.class);

    @Override
    public void accept(EnrichmentMetadata metadata) {
        TransactionHelper.runInTransaction(
            () -> CoreInstance.doPrivileged(metadata.context.repositoryName, session -> {
                EventContextImpl eCtx;
                try {
                    DocumentModel input = session.getDocument(new IdRef(metadata.context.documentRef));
                    eCtx = new DocumentEventContext(session, session.getPrincipal(), input);
                } catch (DocumentNotFoundException e) {
                    if (log.isDebugEnabled()) {
                        log.debug("Document Not Found: " + metadata.context.documentRef);
                    }
                    eCtx = new EventContextImpl();
                }
                eCtx.setProperty(CoreEventConstants.REPOSITORY_NAME, metadata.context.repositoryName);
                eCtx.setProperty(CoreEventConstants.SESSION_ID, session.getSessionId());
                eCtx.setProperty(CATEGORY, DocumentEventCategories.EVENT_DOCUMENT_CATEGORY);
                eCtx.setProperty(ENRICHMENT_METADATA, metadata);
                Event event = eCtx.newEvent(ENRICHMENT_CREATED);
                Framework.getService(EventProducer.class).fireEvent(event);
            })
        );
    }
}
