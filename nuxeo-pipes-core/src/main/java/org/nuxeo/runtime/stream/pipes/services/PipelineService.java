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
package org.nuxeo.runtime.stream.pipes.services;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

import org.nuxeo.ecm.core.event.Event;

/**
 * Works with events and streams using the Pipeline metaphor
 */
public interface PipelineService {

    /**
     * Add a pipe based on its descriptor
     */
    void addPipe(PipeDescriptor descriptor);

    /**
     * Add a pipe that acts on an event
     *
     * @param eventName     The name of the event to act on
     * @param supplierId    A unique id
     * @param eventFunction A function to apply
     * @param isAsync       Is the event listener asynchronous
     * @param consumer      A consumer to consume the result
     */
    <R> void addEventPipe(String eventName, String supplierId,
                          Function<Event, Collection<R>> eventFunction, boolean isAsync, Consumer<R> consumer);

}
