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
package org.nuxeo.ai.enrichment;

import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.services.DocMetadataServiceImpl.ENRICHMENT_ADDED;

import java.io.Serializable;
import org.nuxeo.ai.auto.AutoService;
import org.nuxeo.ai.services.DocMetadataService;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;

/**
 * Listen for modifications that were used to create enrichment metadata.
 * If the property is dirty then the metadata information is removed.
 */
public class EnrichedPropertiesEventListener implements EventListener {

    @Override
    public void handleEvent(Event event) {
        DocumentEventContext docCtx = (DocumentEventContext) event.getContext();
        if (docCtx == null) {
            return;
        }
        DocumentModel doc = docCtx.getSourceDocument();
        Serializable enrichmentAdding = doc.getContextData(ENRICHMENT_ADDED);
        if (enrichmentAdding == null && !doc.isProxy() && doc.hasFacet(ENRICHMENT_FACET)) {
            Framework.getService(AutoService.class).autoApproveDirtyProperties(doc);
            Framework.getService(DocMetadataService.class).removeItemsForDirtyProperties(doc);
        }
    }
}
