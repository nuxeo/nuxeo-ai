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
import static org.nuxeo.ai.AIConstants.ENRICHMENT_ITEMS;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_SCHEMA_NAME;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_INPUT_DOCPROP_PROPERTY;
import static org.nuxeo.ai.services.DocMetadataServiceImpl.ENRICHMENT_ADDED;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

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
            checkAndCleanEnrichedProperties(doc);
        }
    }

    /**
     * Finds existing enrichment metadata, checks to see if any of the properties used to create
     * that metadata have been modified.  If they are modified then it removes the enrichment data for that
     * property.
     */
    protected void checkAndCleanEnrichedProperties(DocumentModel doc) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> enriched = (List) doc.getProperty(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS);
        List<Map<String, Object>> cleanEnriched = new ArrayList<>();

        if (enriched == null || enriched.isEmpty()) {
            return;
        }

        enriched.forEach(entry -> {
            if (!hadBeenModified(doc, entry)) {
                cleanEnriched.add(entry);
            }
        });

        if (cleanEnriched.size() != enriched.size()) {
            //We made some changes lets update
            doc.setProperty(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS, cleanEnriched);
            if (cleanEnriched.isEmpty()) {
                doc.removeFacet(ENRICHMENT_FACET);
            }
        }
    }

    /**
     * Have one of the supplied properties been modified?
     */
    protected boolean hadBeenModified(DocumentModel doc, Map<String, Object> enriched) {
        String[] props = (String[]) enriched.get(ENRICHMENT_INPUT_DOCPROP_PROPERTY);
        if (props != null) {
            for (String propName : props) {
                try {
                    Property prop = doc.getProperty(propName);
                    if (prop != null && prop.isDirty()) {
                        return true;
                    }
                } catch (PropertyNotFoundException e) {
                    // Just ignore
                }
            }
        }
        return false;
    }
}
