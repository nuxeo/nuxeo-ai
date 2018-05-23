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
import java.util.function.Function;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.stream.LogConfigDescriptor;
import org.nuxeo.runtime.stream.pipes.streams.Initializable;

@XObject("pipe")
public class PipeDescriptor {

    @XNode("@id")
    public String id;
    @XNodeMap(value = "option", key = "@name", type = HashMap.class, componentType = String.class)
    public Map<String, String> options = new HashMap<>();
    @XNode("@enabled")
    protected boolean enabled = true;
    @SuppressWarnings("rawtypes")
    @XNode("@function")
    protected Class<? extends Function> function;
    @XNode("supplier")
    protected Supplier supplier;
    @XNode("consumer")
    protected Consumer consumer;

    public void validate() {
        StringBuilder errors = new StringBuilder();

        if (StringUtils.isBlank(id)) {
            errors.append("You must specify an id\n");
        }
        if (function == null) {
            errors.append("You must specify a function\n");
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

        if (other.function != null) {
            function = other.function;
        }

        if (other.supplier != null && other.supplier.events != null && !other.supplier.events.isEmpty()) {
            supplier.events = other.supplier.events;
        }

        if (other.consumer != null && other.consumer.streams != null && !other.consumer.streams.isEmpty()) {
            consumer.streams = other.consumer.streams;
        }

        //Can't be null
        options.putAll(other.options);
    }

    @SuppressWarnings("rawtypes")
    public Function getFunction() {
        if (function != null) {
            try {
                Function func = function.newInstance();
                if (func instanceof Initializable) {
                    ((Initializable) func).init(options);
                }
                return func;
            } catch (IllegalAccessException | InstantiationException e) {
                throw new NuxeoException("PipeDescriptor must define a valid Function class", e);
            }
        }
        return null;
    }

    @XObject("supplier")
    public static class Supplier {

        @XNodeList(value = "event", type = ArrayList.class, componentType = String.class)
        public List<String> events = new ArrayList<>(0);

    }

    @XObject("consumer")
    public static class Consumer {

        @XNodeList(value = "stream", type = ArrayList.class, componentType = LogConfigDescriptor.StreamDescriptor.class)
        public List<LogConfigDescriptor.StreamDescriptor> streams = new ArrayList<>(0);

    }

}
