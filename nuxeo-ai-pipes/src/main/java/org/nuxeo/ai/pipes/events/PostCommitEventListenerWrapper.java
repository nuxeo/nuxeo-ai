/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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

import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.PostCommitEventListener;

/**
 * Wraps a standard EventListener as a PostCommitEventListener.
 */
public class PostCommitEventListenerWrapper implements PostCommitEventListener {

    protected final EventListener eventListener;

    public PostCommitEventListenerWrapper(EventListener eventListener) {
        this.eventListener = eventListener;
    }

    @Override
    public void handleEvent(EventBundle events) {
        for (Event event : events) {
            this.eventListener.handleEvent(event);
        }
    }
}
