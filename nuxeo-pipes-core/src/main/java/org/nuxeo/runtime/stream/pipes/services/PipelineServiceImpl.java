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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.EventListenerDescriptor;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.log.LogAppender;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.metrics.NuxeoMetricSet;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.stream.LogConfigDescriptor;
import org.nuxeo.runtime.stream.StreamService;
import org.nuxeo.runtime.stream.pipes.consumers.LogAppenderConsumer;
import org.nuxeo.runtime.stream.pipes.events.DynamicEventListenerDescriptor;
import org.nuxeo.runtime.stream.pipes.events.EventConsumer;

public class PipelineServiceImpl extends DefaultComponent implements PipelineService {

    public static final String ROUTE_AP = "pipes";
    public static final String PIPES_CONFIG = "nuxeo.pipes.config.name";
    private static final Log log = LogFactory.getLog(PipelineServiceImpl.class);

    private String pipeConfigName;
    protected final Map<String, PipeDescriptor> configs = new HashMap<>();
    protected final List<EventListenerDescriptor> listenerDescriptors = new ArrayList<>();

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (ROUTE_AP.equals(extensionPoint)) {
            PipeDescriptor descriptor = (PipeDescriptor) contribution;
            PipeDescriptor original = this.configs.get(descriptor.id);
            if (original != null) {
                original.merge(descriptor);
                descriptor = original;
            }
            descriptor.validate();
            this.configs.put(descriptor.id, descriptor);
        }
    }

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        pipeConfigName = Framework.getProperty(PIPES_CONFIG, "pipes");
        this.configs.forEach((key, value) -> addPipe(value));
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);

        //Remove event listeners
        EventService eventService = Framework.getService(EventService.class);
        listenerDescriptors.forEach(eventService::removeEventListener);
    }

    protected List<Consumer<Record>> getConsumers(PipeDescriptor descriptor) {
        List<Consumer<Record>> consumers = new ArrayList<>();
        List<LogConfigDescriptor.StreamDescriptor> streams = descriptor.consumer.streams;
        streams.forEach(s -> consumers.add(addLogConsumer(s.name, s.size)));
        return consumers;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addPipe(PipeDescriptor descriptor) {
        if (descriptor != null && descriptor.enabled) {
            descriptor.supplier.events.forEach(e -> {
                NuxeoMetricSet pipeMetrics = new NuxeoMetricSet("nuxeo", pipeConfigName, descriptor.id);
                List<Consumer<Record>> consumers = getConsumers(descriptor);
                consumers.forEach(consumer -> {
                    if (log.isDebugEnabled()) {
                        log.debug(String.format("Listening for %s event and sending it to %s", e, consumer.toString()));
                    }
                    addEventPipe(e, pipeMetrics, descriptor.getFunction(), consumer);
                });

            });
        }
    }

    @Override
    public <R> void addEventPipe(String eventName, NuxeoMetricSet metricSet, Function<Event, R> function, Consumer<R> consumer) {
        EventService eventService = Framework.getService(EventService.class);
        EventConsumer<R> eventConsumer = new EventConsumer<>(function, consumer);
        eventConsumer.withMetrics(metricSet);
        EventListenerDescriptor listenerDescriptor = new DynamicEventListenerDescriptor(eventName, eventConsumer);
        listenerDescriptors.add(listenerDescriptor);
        eventService.addEventListener(listenerDescriptor);
    }

    protected LogAppenderConsumer addLogConsumer(String logName, int size) {
        LogManager manager = Framework.getService(StreamService.class).getLogManager(pipeConfigName);
        manager.createIfNotExists(logName, size);
        LogAppender<Record> appender = manager.getAppender(logName);
        return new LogAppenderConsumer(appender);
    }
}
