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
package org.nuxeo.ai.pipes.events;

import java.util.List;
import java.util.Map;
import org.nuxeo.ai.pipes.streams.Initializable;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

/**
 * An Event listener that adds a key in the DocumentEventContext if a doc property is dirty
 */
public class DirtyEventListener implements EventListener, Initializable {

    public static final String DIRTY = "dirty_prop_";

    public static final String DIRTY_EVENT_NAME = "beforeDocumentModification";

    protected List<String> properties;

    public DirtyEventListener(Map<String, String> options) {
        init(options);
    }

    public static String makeDirtyKey(String propertyName) {
        return DIRTY + propertyName;
    }

    @Override
    public void init(Map<String, String> options) {
        properties = propsList(options.get("properties"));
    }

    @Override
    public void handleEvent(Event event) {
        DocumentEventContext docCtx = (DocumentEventContext) event.getContext();
        if (docCtx == null) {
            return;
        }
        DocumentModel doc = docCtx.getSourceDocument();
        if (doc != null && !doc.isProxy()) {
            properties.forEach(propName -> {
                try {
                    Property prop = doc.getProperty(propName);
                    if (prop != null && prop.isDirty()) {
                        docCtx.setProperty(makeDirtyKey(propName), true);
                    }
                } catch (PropertyNotFoundException e) {
                    //Ignore exception
                }
            });
        }
    }
}
