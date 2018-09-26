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
package org.nuxeo.ai.pipes.filters;


import static org.nuxeo.ai.pipes.events.DirtyEventListener.makeDirtyKey;

import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ai.pipes.filters.Filter.EventFilter;
import org.nuxeo.ai.pipes.streams.Initializable;

/**
 * A filter that returns true if dirty property is found in the document context.
 * It is dependent on {@link org.nuxeo.ai.pipes.events.DirtyEventListener} having run first.
 */
public class DirtyPropertyFilter implements EventFilter, Initializable {

    protected List<String> properties;

    @Override
    public void init(Map<String, String> options) {
        properties = propsList(options.get("properties"));
    }

    @Override
    public boolean test(Event event) {
        DocumentEventContext docCtx = (DocumentEventContext) event.getContext();
        return docCtx != null && properties.stream().anyMatch(p -> docCtx.hasProperty(makeDirtyKey(p)));
    }
}
