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

package org.nuxeo.ai.similar.content.mock;

import static org.nuxeo.ai.similar.content.listeners.DocumentIndexedListener.SIMILAR_DOCUMENT_IDS_PARAM;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

public class ResolveDuplicatesListener implements EventListener {

    public static final AtomicReference<DocumentModel> docRef = new AtomicReference<>();

    public static final List<String> similarIds = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void handleEvent(Event event) {
        DocumentEventContext ctx = (DocumentEventContext) event.getContext();
        docRef.set(ctx.getSourceDocument());

        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) ctx.getProperty(SIMILAR_DOCUMENT_IDS_PARAM);
        similarIds.clear();
        similarIds.addAll(ids);
    }
}
