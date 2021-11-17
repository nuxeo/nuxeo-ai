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

import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.BEFORE_DOC_UPDATE;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_REMOVED;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.similar.content.operations.DedupIndexOperation;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;

import com.sun.jersey.core.spi.factory.ResponseImpl;

/**
 * Asynchronous listener to manage Insight deduplication index on repository documents.
 */
public class DedupListener implements PostCommitEventListener {

    private static final Logger log = LogManager.getLogger(DedupListener.class);

    @Override
    public void handleEvent(EventBundle eventBundle) {
        eventBundle.forEach(this::handleEvent);
    }

    protected void handleEvent(Event event) {
        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
            return;
        }
        DocumentEventContext docCtx = (DocumentEventContext) ctx;
        DocumentModel doc = docCtx.getSourceDocument();
        AutomationService automationService = Framework.getService(AutomationService.class);
        OperationContext opCtx = new OperationContext();
        if (event.getName().equals(DOCUMENT_CREATED)) {
            
        }

        if (event.getName().equals(BEFORE_DOC_UPDATE)) {

        }

        if (event.getName().equals(DOCUMENT_REMOVED)) {
            opCtx.setInput(doc);
            ResponseImpl response;
            try {
                response = (ResponseImpl) automationService.run(opCtx, DedupIndexOperation.ID, null);
            } catch (OperationException e) {
                throw new NuxeoException(e);
            }
            if(response == null || response.getStatus() != Response.Status.OK.getStatusCode()){
                log.error("Index deletion failed - [response={}]", response);
            }
        }
    }
}
