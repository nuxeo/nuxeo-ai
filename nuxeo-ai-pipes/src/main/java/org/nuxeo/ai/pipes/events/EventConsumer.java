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

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.pipes.functions.MetricsProducer;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.metrics.NuxeoMetricSet;

/**
 * An event handler that uses a function and consumer.
 */
public class EventConsumer<R> implements EventListener, MetricsProducer {

    private static final Logger log = LogManager.getLogger(EventConsumer.class);

    private final Function<Event, Collection<R>> function;

    private final Consumer<R> consumer;

    private long handled = 0;

    private long consumed = 0;

    public EventConsumer(Function<Event, Collection<R>> function, Consumer<R> consumer) {
        this.function = function;
        this.consumer = consumer;
    }

    @Override
    public void handleEvent(Event event) {
        handled++;
        Collection<R> applied = function.apply(event);
        if (applied != null && !applied.isEmpty()) {
            String id;
            if (event instanceof DocumentEventContext) {
                id = ((DocumentEventContext) event).getSourceDocument().getId();
            } else {
                id = null;
            }
            applied.forEach(i -> {
                if (i == null) {
                    log.error("Null value returned from function for event {}; document {}", event.getName(), id);
                } else {
                    consumer.accept(i);
                }

                consumed++;
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Consumed event %s", event.getName()));
                }
            });
        }
    }

    @Override
    public void withMetrics(NuxeoMetricSet nuxeoMetrics) {
        nuxeoMetrics.putGauge(() -> handled, "events");
        nuxeoMetrics.putGauge(() -> consumed, "consumed");
    }
}
