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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.stream.LogConfigDescriptor;
import org.nuxeo.runtime.stream.pipes.filters.DirtyPropertyFilter;
import org.nuxeo.runtime.stream.pipes.filters.DocumentEventFilter;
import org.nuxeo.runtime.stream.pipes.filters.Filter;
import org.nuxeo.runtime.stream.pipes.filters.Filter.DocumentFilter;
import org.nuxeo.runtime.stream.pipes.filters.Filter.EventFilter;
import org.nuxeo.runtime.stream.pipes.functions.PreFilterFunction;
import org.nuxeo.runtime.stream.pipes.functions.PropertiesToStream;
import org.nuxeo.runtime.stream.pipes.streams.Initializable;

@XObject("pipe")
public class PipeDescriptor {

    public static final Class<? extends Function> DEFAULT_TRANSFORMER = PropertiesToStream.class;

    @XNode("@id")
    public String id;
    @XNode("@enabled")
    protected boolean enabled = true;
    @XNode("@async")
    protected Boolean isAsync = true;

    @XNode("supplier")
    protected Supplier supplier;
    @XNode("consumer")
    protected Consumer consumer;
    @XNode("transformer")
    protected TransformingFunction transformer;

    public void validate() {
        StringBuilder errors = new StringBuilder();

        if (StringUtils.isBlank(id)) {
            errors.append("You must specify an id\n");
        }
        if (transformer == null) {
            errors.append("You must specify a transformer\n");
        }
        if (supplier == null || supplier.events == null || supplier.events.isEmpty()) {
            errors.append("Invalid supplier configuration, you must specify an event\n");
        }
        if (consumer == null || consumer.streams == null || consumer.streams.isEmpty()) {
            errors.append("Invalid consumer configuration, you must specify at least consumer\n");
        }
        if (errors.length() > 0) {
            throw new NuxeoException(errors.toString());
        }

    }

    public void merge(PipeDescriptor other) {

        if (!id.equals(other.id)) {
            //These are not the same
            return;
        }
        if (other.enabled != enabled) {
            enabled = other.enabled;
        }

        if (other.transformer != null) {
            transformer.options.putAll(other.transformer.options);
            if (other.transformer.function != null) {
                transformer.function = other.transformer.function;
            }
        }

        if (other.supplier != null && other.supplier.events != null && !other.supplier.events.isEmpty()) {
            supplier.events = other.supplier.events;
        }

        if (other.consumer != null && other.consumer.streams != null && !other.consumer.streams.isEmpty()) {
            consumer.streams = other.consumer.streams;
        }
    }

    /**
     * Get the function that acts on the specified event
     */
    public Function<Event, Collection<Record>> getFunction(PipeEvent event) {
        try {
            if (transformer.function == null) {
                transformer.function = DEFAULT_TRANSFORMER;
            }
            Function func = transformer.function.getDeclaredConstructor().newInstance();
            if (func instanceof Initializable) {
                ((Initializable) func).init(transformer.options);
            }
            if (func instanceof PreFilterFunction) {
                ((PreFilterFunction) func).setFilter(getEventFilter(event));
            }
            return func;
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | NoSuchMethodException | InvocationTargetException e) {
            throw new NuxeoException(String.format("PipeDescriptor %s must define a valid transformer function", this.id), e);
        }
    }

    /**
     * Indicates that this event has a dirtycheck filter
     */
    public boolean hasDirtyCheckFilter(PipeEvent event) {
        return event.filters.stream()
                            .anyMatch(pipeFilter -> DirtyPropertyFilter.class.isAssignableFrom(pipeFilter.clazz));
    }

    /**
     * Get event filters for the specified event
     */
    public Filter<Event> getEventFilter(PipeEvent event) {
        if (event != null) {
            try {
                DocumentEventFilter.Builder builder = new DocumentEventFilter.Builder();
                for (PipeFilter filter : event.filters) {
                    Filter theFilter = filter.clazz.newInstance();
                    if (theFilter instanceof Initializable) {
                        filter.options.putAll(event.options);
                        ((Initializable) theFilter).init(filter.options);
                    }
                    if (theFilter instanceof DocumentFilter) {
                        builder.withDocumentFilter((DocumentFilter) theFilter);
                    } else {
                        builder.withEventFilter((EventFilter) theFilter);
                    }
                }
                return builder.build();
            } catch (IllegalAccessException | InstantiationException | ClassCastException e) {
                throw new NuxeoException("PipeDescriptor must define valid event filters", e);
            }
        }
        return null;
    }

    @XObject("supplier")
    public static class Supplier {

        @XNodeList(value = "event", type = ArrayList.class, componentType = PipeEvent.class)
        public List<PipeEvent> events = new ArrayList<>(0);

    }

    @XObject("event")
    public static class PipeEvent {

        @XNode("@name")
        public String name;

        @XNodeMap(value = "option", key = "@name", type = HashMap.class, componentType = String.class)
        public Map<String, String> options = new HashMap<>();

        @XNodeList(value = "filter", type = ArrayList.class, componentType = PipeFilter.class)
        public List<PipeFilter> filters = new ArrayList<>(0);
    }

    @XObject("filter")
    public static class PipeFilter {

        @XNodeMap(value = "option", key = "@name", type = HashMap.class, componentType = String.class)
        public Map<String, String> options = new HashMap<>();
        @XNode("@class")
        protected Class<? extends EventFilter> clazz;
    }

    @XObject("consumer")
    public static class Consumer {

        @XNodeList(value = "stream", type = ArrayList.class, componentType = LogConfigDescriptor.StreamDescriptor.class)
        public List<LogConfigDescriptor.StreamDescriptor> streams = new ArrayList<>(0);

    }

    @XObject("transformer")
    public static class TransformingFunction {

        @XNodeMap(value = "option", key = "@name", type = HashMap.class, componentType = String.class)
        public Map<String, String> options = new HashMap<>();

        @SuppressWarnings("rawtypes")
        @XNode("@class")
        protected Class<? extends Function> function;

    }
}
