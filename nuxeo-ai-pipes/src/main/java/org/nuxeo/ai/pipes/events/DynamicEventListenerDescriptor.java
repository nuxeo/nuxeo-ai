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

import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.EventListenerDescriptor;

/**
 * An implementation of an EventListenerDescriptor that is created dynamically
 */
public class DynamicEventListenerDescriptor extends EventListenerDescriptor {

    public DynamicEventListenerDescriptor(String eventName, EventListener eventListener, boolean isAsync,
            boolean isPostCommit) {
        this(eventName + "_" + UUID.randomUUID(), isPostCommit, isAsync, eventName, eventListener);
    }

    public DynamicEventListenerDescriptor(String name, boolean isPostCommit, boolean isAsync, String eventName,
            EventListener eventListener) {
        this.name = name;
        this.isPostCommit = isPostCommit;
        this.isAsync = isAsync;
        this.events = new HashSet<>(Collections.singletonList(eventName));
        this.inLineListener = isPostCommit ? null : eventListener;
        this.postCommitEventListener = isPostCommit ? new PostCommitEventListenerWrapper(eventListener) : null;
    }

    @Override
    public void initListener() {
        //Nothing to do
    }

}
