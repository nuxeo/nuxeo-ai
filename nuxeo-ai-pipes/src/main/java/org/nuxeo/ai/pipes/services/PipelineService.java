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
package org.nuxeo.ai.pipes.services;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.lib.stream.computation.Record;

/**
 * Works with events and streams using the Pipeline metaphor.
 */
public interface PipelineService {

    /**
     * Add a pipe based on its descriptor.
     */
    void addPipe(PipeDescriptor descriptor);

    /**
     * Adds a special listener for binary text
     */
    void addBinaryTextListener(String eventName, String logName, int partitions, String propertyName, int windowSizeSeconds);

    /**
     * Get a log consumer if its already configured by the service
     */
    Consumer<Record> getConsumer(String logName);

    /**
     * Add a pipe that acts on an event.
     * The eventFunction is applied to each event and any result is sent to the consumer.
     *
     * @param eventName     The name of the event to act on
     * @param pipelineId    A unique id for this pipeline, used as a description
     * @param eventFunction A function to apply for each event
     * @param isAsync       Is the event listener asynchronous
     * @param consumer      A consumer to consume the result
     */
    <R> void addEventPipe(String eventName, String pipelineId,
                          Function<Event, Collection<R>> eventFunction, boolean isAsync, Consumer<R> consumer);

    /**
     * Add an event listener and consumer
     * @param eventName The name of the event to act on
     * @param isAsync Is the event listener asynchronous
     * @param eventConsumer A consumer to consume the result
     */
    void addEventListener(String eventName, boolean isAsync, EventListener eventConsumer);
}
