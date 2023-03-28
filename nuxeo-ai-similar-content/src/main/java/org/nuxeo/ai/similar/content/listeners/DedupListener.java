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

import static org.nuxeo.ai.pipes.functions.PropertyUtils.isStrictModeEnabled;
import static org.nuxeo.ai.similar.content.DedupConstants.CONF_DEDUPLICATION_CONFIGURATION;
import static org.nuxeo.ai.similar.content.DedupConstants.CONF_LISTENER_ENABLE;
import static org.nuxeo.ai.similar.content.DedupConstants.DEFAULT_CONFIGURATION;
import static org.nuxeo.ai.similar.content.DedupConstants.SKIP_INDEX_FLAG_UPDATE;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.BEFORE_DOC_UPDATE;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_REMOVED;

import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.similar.content.services.SimilarContentService;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;

/**
 * Asynchronous listener to manage Insight deduplication index on repository documents.
 */
public class DedupListener implements EventListener {

    private static final Logger log = LogManager.getLogger(DedupListener.class);

    protected static final String PICTURE_VIEWS_GENERATED = "pictureViewsGenerationDone";

    @Override
    public void handleEvent(Event event) {
        // Check if the listener is activated
        boolean enable = Boolean.parseBoolean(Framework.getProperty(CONF_LISTENER_ENABLE, "false"));
        if (!enable) {
            return;
        }

        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
            return;
        }

        DocumentEventContext docCtx = (DocumentEventContext) ctx;
        DocumentModel doc = docCtx.getSourceDocument();
        // Skip the listener if skip update flag has been set
        if (doc.getContextData(SKIP_INDEX_FLAG_UPDATE) != null) {
            return;
        }

        SimilarContentService scs = Framework.getService(SimilarContentService.class);
        String configuration = Framework.getProperty(CONF_DEDUPLICATION_CONFIGURATION, DEFAULT_CONFIGURATION);
        if (!scs.test(configuration, doc)) {
            log.debug("Document {} doesn't pass the test of {} configuration", doc.getId(), configuration);
            return;
        }

        try {
            String xpath = scs.getXPath(configuration);
            if ((DOCUMENT_CREATED.equals(event.getName()) && !isStrictModeEnabled()) || PICTURE_VIEWS_GENERATED.equals(
                    event.getName())) {
                if (!scs.index(doc, xpath)) {
                    log.error("Index for Document {} and xpath {} failed on event {}", doc.getId(), xpath,
                            event.getName());
                }
            } else if (BEFORE_DOC_UPDATE.equals(event.getName()) && doc.getProperty(xpath).isDirty()) {
                // skip the next update event when updating the document
                doc.putContextData(SKIP_INDEX_FLAG_UPDATE, true);
                if (doc.getPropertyValue(xpath) == null) {
                    if (!scs.delete(doc, xpath)) {
                        log.error("Delete for Document {} and xpath {} failed", doc.getId(), xpath);
                    }
                } else {
                    if (!scs.index(doc, xpath)) {
                        log.error("Index for Document {} and xpath {} failed", doc.getId(), xpath);
                    }
                }
            } else if (DOCUMENT_REMOVED.equals(event.getName())) {
                if (!scs.delete(doc, null)) {
                    log.error("Delete for Document {} and xpath {} failed on event {}", doc.getId(), xpath,
                            event.getName());
                }
            }
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }

}
