/*
 * (C) Copyright 2006-2021 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Nuxeo
 */
package org.nuxeo.ai.similar.content.listeners;

import static org.nuxeo.ai.similar.content.DedupConstants.CONF_DEDUPLICATION_CONFIGURATION;
import static org.nuxeo.ai.similar.content.DedupConstants.CONF_LISTENER_ENABLE;
import static org.nuxeo.ai.similar.content.DedupConstants.DEFAULT_CONFIGURATION;
import static org.nuxeo.ai.similar.content.DedupConstants.SKIP_INDEX_FLAG_UPDATE;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.BEFORE_DOC_UPDATE;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_REMOVED;

import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.similar.content.operation.DedupDeleteIndexOperation;
import org.nuxeo.ai.similar.content.operation.DedupIndexOperation;
import org.nuxeo.ai.similar.content.services.SimilarContentService;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;
import com.sun.jersey.core.spi.factory.ResponseImpl;

/**
 * Asynchronous listener to manage Insight deduplication index on repository documents.
 */
public class DedupListener implements EventListener {

    private static final Logger log = LogManager.getLogger(DedupListener.class);

    @Override
    public void handleEvent(Event event) {
        // Check if the listener is activated
        boolean enable = Boolean.parseBoolean(Framework.getProperty(CONF_LISTENER_ENABLE, "true"));
        if (!enable) {
            return;
        }

        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
            return;
        }

        String configuration = Framework.getProperty(CONF_DEDUPLICATION_CONFIGURATION, DEFAULT_CONFIGURATION);
        DocumentEventContext docCtx = (DocumentEventContext) ctx;
        DocumentModel doc = docCtx.getSourceDocument();

        // Skip the listener if skip update flag has been set
        if (doc.getContextData(SKIP_INDEX_FLAG_UPDATE) != null) {
            return;
        }

        AutomationService automationService = Framework.getService(AutomationService.class);
        SimilarContentService similarContentService = Framework.getService(SimilarContentService.class);
        OperationContext opCtx = new OperationContext();
        opCtx.setCoreSession(docCtx.getCoreSession());
        opCtx.setInput(doc);
        ResponseImpl response = null;
        if (!similarContentService.test(configuration, doc)) {
            return;
        }

        String xpath = similarContentService.getXPath(configuration);
        try {
            if (event.getName().equals(DOCUMENT_CREATED)) {
                Map<String, String> params = new HashMap<>();
                params.put("xpath", xpath);
                response = (ResponseImpl) automationService.run(opCtx, DedupIndexOperation.ID, params);
            }

            if (event.getName().equals(BEFORE_DOC_UPDATE)) {
                if (doc.getProperty(xpath).isDirty()) {
                    // skip the next update event when updating the document
                    doc.putContextData(SKIP_INDEX_FLAG_UPDATE, true);
                    Map<String, String> params = new HashMap<>();
                    params.put("xpath", xpath);
                    if (doc.getPropertyValue(xpath) == null) {
                        response = (ResponseImpl) automationService.run(opCtx, DedupDeleteIndexOperation.ID, params);
                    } else {
                        response = (ResponseImpl) automationService.run(opCtx, DedupIndexOperation.ID, params);
                    }
                }
            }

            if (event.getName().equals(DOCUMENT_REMOVED)) {
                response = (ResponseImpl) automationService.run(opCtx, DedupDeleteIndexOperation.ID, new HashMap<>());
            }

        } catch (OperationException e) {
            throw new NuxeoException(e);
        }

        if (response == null || response.getStatus() != Response.Status.OK.getStatusCode()) {
            log.error("Index deletion failed - [response={}]", response);
        }
    }

}
