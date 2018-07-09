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

import static org.nuxeo.runtime.stream.pipes.events.DirtyEventListener.DIRTY_EVENT_NAME;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.EventListenerDescriptor;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.lib.stream.log.internals.CloseableLogAppender;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.metrics.MetricsService;
import org.nuxeo.runtime.metrics.NuxeoMetricSet;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.stream.LogConfigDescriptor;
import org.nuxeo.runtime.stream.StreamService;
import org.nuxeo.runtime.stream.pipes.consumers.LogAppenderConsumer;
import org.nuxeo.runtime.stream.pipes.events.DirtyEventListener;
import org.nuxeo.runtime.stream.pipes.events.DynamicEventListenerDescriptor;
import org.nuxeo.runtime.stream.pipes.events.EventConsumer;
import org.nuxeo.runtime.stream.pipes.functions.BinaryTextListener;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;

public class PipelineServiceImpl extends DefaultComponent implements PipelineService {

    public static final String ROUTE_AP = "pipes";

    public static final String TEXT_AP = "text";

    public static final String PIPES_CONFIG = "nuxeo.pipes.config.name";

    private static final Log log = LogFactory.getLog(PipelineServiceImpl.class);

    protected final Map<String, PipeDescriptor> configs = new HashMap<>();

    protected final List<BinaryTextDescriptor> textConfigs = new ArrayList<>();

    protected final List<EventListenerDescriptor> listenerDescriptors = new ArrayList<>();

    protected final Map<String, LogAppenderConsumer> logAppenderConsumers = new HashMap<>();

    protected final MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());

    private String pipeConfigName;

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
        } else if (TEXT_AP.equals(extensionPoint)) {
            BinaryTextDescriptor descriptor = (BinaryTextDescriptor) contribution;
            textConfigs.add(descriptor);
        }
    }

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        pipeConfigName = Framework.getProperty(PIPES_CONFIG, "pipes");
        this.configs.forEach((key, value) -> addPipe(value));
        this.textConfigs.forEach(d ->
                                 d.consumer.streams.forEach(s ->
                                     addBinaryTextListener(d.eventName, s.name, s.size, d.propertyName, d.windowSize)
                                 )
        );
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);

        //Remove event listeners
        EventService eventService = Framework.getService(EventService.class);
        listenerDescriptors.forEach(eventService::removeEventListener);

        logAppenderConsumers.values().forEach(LogAppenderConsumer::close);
    }

    /**
     * Get the consumers defined by the descriptor
     */
    protected List<Consumer<Record>> getConsumers(PipeDescriptor descriptor) {
        List<Consumer<Record>> consumers = new ArrayList<>();
        List<LogConfigDescriptor.StreamDescriptor> streams = descriptor.consumer.streams;
        streams.forEach(s -> consumers.add(addLogConsumer(s.name, s.size)));
        return consumers;
    }

    @Override
    public void addPipe(PipeDescriptor descriptor) {
        if (descriptor != null && descriptor.enabled) {
            descriptor.supplier.events.forEach(e -> {
                List<Consumer<Record>> consumers = getConsumers(descriptor);
                if (descriptor.hasDirtyCheckFilter(e)) {
                    //If we have a dirty check then add a special pre-commit listener
                    addDirtyCheckListener(DIRTY_EVENT_NAME, e.options);
                }
                consumers.forEach(consumer -> {
                    if (log.isDebugEnabled()) {
                        log.debug(String.format("Listening for %s event and sending it to %s", e, consumer.toString()));
                    }
                    addEventPipe(e.name, descriptor.id, descriptor.getFunction(e), descriptor.isAsync, consumer);
                });
            });
        }
    }

    /**
     * Add a <code>DirtyEventListener</code> to listen for properties that are dirty
     */
    protected void addDirtyCheckListener(String eventName, Map<String, String> options) {
        DirtyEventListener dirtyEventListener = new DirtyEventListener(options);
        addEventListener(eventName, false, dirtyEventListener);
    }

    /**
     * Adds a listener for binary text and send the result to the specified log
     */
    @Override
    public void addBinaryTextListener(String eventName, String logName, int partitions, String propertyName, int windowSizeSeconds) {
        addLogConsumer(logName, partitions);
        BinaryTextListener textListener = new BinaryTextListener(logName, propertyName, windowSizeSeconds);
        addEventListener(eventName, false, textListener);
    }

    @Override
    public Consumer<Record> getConsumer(String logName) {
        return logAppenderConsumers.get(logName);
    }

    @Override
    public <R> void addEventPipe(String eventName, String supplierId,
                                 Function<Event, Collection<R>> eventFunction, boolean isAsync, Consumer<R> consumer) {
        NuxeoMetricSet pipeMetrics = new NuxeoMetricSet("nuxeo", "streams", eventName, supplierId);
        EventConsumer<R> eventConsumer = new EventConsumer<>(eventFunction, consumer);
//        eventConsumer.withMetrics(pipeMetrics);
//        registry.registerAll(pipeMetrics);
        addEventListener(eventName, isAsync, eventConsumer);
    }

    /**
     * Add an <code>EventListener</code> using the <code>EventService</code>
     */
    @Override
    public void addEventListener(String eventName, boolean isAsync, EventListener eventConsumer) {
        EventService eventService = Framework.getService(EventService.class);
        EventListenerDescriptor listenerDescriptor = new DynamicEventListenerDescriptor(eventName, eventConsumer, isAsync);
        listenerDescriptors.add(listenerDescriptor);
        eventService.addEventListener(listenerDescriptor);
    }

    /**
     * Create a <code>LogAppenderConsumer</code> for the specified log/stream</code>
     */
    protected LogAppenderConsumer addLogConsumer(String logName, int size) {
        LogManager manager = Framework.getService(StreamService.class).getLogManager(pipeConfigName);
        manager.createIfNotExists(logName, size);
        CloseableLogAppender<Record> appender = (CloseableLogAppender) manager.getAppender(logName);
        LogAppenderConsumer consumer = new LogAppenderConsumer(appender);
        logAppenderConsumers.put(logName, consumer);
        return consumer;
    }
}
