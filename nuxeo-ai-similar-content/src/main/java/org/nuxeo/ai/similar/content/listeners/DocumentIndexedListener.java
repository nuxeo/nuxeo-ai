/*
 * (C) Copyright 2006-2021 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 * Contributors:
 *    Andrei Nechaev
 *
 */

package org.nuxeo.ai.similar.content.listeners;

import static org.nuxeo.ai.similar.content.DedupConstants.CONF_DEDUPLICATION_CONFIGURATION;
import static org.nuxeo.ai.similar.content.DedupConstants.DEFAULT_CONFIGURATION;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.similar.content.services.SimilarContentService;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;

/**
 * Listener responsible for calling {@link SimilarContentService#findSimilar(CoreSession, DocumentModel, String)}
 * with provided document. Consequentially, it fires <b>similarDocumentsFound</b> event with all similar documents' ids
 * attached at <b>similarIds</b> {@link DocumentEventContext} property
 */
public class DocumentIndexedListener implements PostCommitEventListener {

    private static final Logger log = LogManager.getLogger(DocumentIndexedListener.class);

    public static final String SIMILAR_DOCUMENTS_FOUND_EVENT = "similarDocumentsFound";

    public static final String SIMILAR_DOCUMENT_IDS_PARAM = "similarIds";

    @Override
    public void handleEvent(EventBundle bundle) {
        bundle.forEach(this::handleEvent);
    }

    protected void handleEvent(Event event) {
        if (!(event.getContext() instanceof DocumentEventContext)) {
            return;
        }

        DocumentEventContext ctx = (DocumentEventContext) event.getContext();
        DocumentModel doc = ctx.getSourceDocument();
        SimilarContentService scs = Framework.getService(SimilarContentService.class);
        String configuration = Framework.getProperty(CONF_DEDUPLICATION_CONFIGURATION, DEFAULT_CONFIGURATION);
        if (!scs.test(configuration, doc)) {
            log.debug("Document {} doesn't pass the test of '{}' configuration", doc.getId(), configuration);
            return;
        }

        CoreSession session = doc.getCoreSession();
        List<String> similarIds;
        try {
            String xpath = scs.getXPath(configuration);
            List<DocumentModel> similar = scs.findSimilar(session, doc, xpath);
            if (similar.isEmpty()) {
                log.info("No similar documents found for document {} and xpath {}", doc.getId(), xpath);
                return;
            }

            similarIds = similar.stream().map(DocumentModel::getId).collect(Collectors.toList());
        } catch (IOException e) {
            throw new NuxeoException(e);
        }

        DocumentEventContext docCtx = new DocumentEventContext(session, session.getPrincipal(), doc);

        Map<String, Serializable> props = new HashMap<>();
        props.put(SIMILAR_DOCUMENT_IDS_PARAM, (Serializable) similarIds);
        docCtx.setProperties(props);
        Framework.getService(EventService.class).fireEvent(SIMILAR_DOCUMENTS_FOUND_EVENT, docCtx);
    }
}
