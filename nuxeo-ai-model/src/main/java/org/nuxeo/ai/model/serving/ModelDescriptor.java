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
package org.nuxeo.ai.model.serving;

import static org.nuxeo.runtime.stream.pipes.functions.PropertyUtils.notNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.stream.pipes.functions.Predicates;

/**
 * Definition of an AI model at runtime
 */
@XObject("model")
public class ModelDescriptor {

    private static final Class<? extends RuntimeModel> DEFAULT_MODEL_CLASS = TFRuntimeModel.class;

    @XNode("@id")
    public String id;

    @XNodeList(value = "input", type = HashSet.class, componentType = Property.class)
    public Set<Property> inputs = new HashSet<>();

    @XNodeList(value = "output", type = HashSet.class, componentType = String.class)
    public Set<String> outputs = new HashSet<>();

    /**
     * Endpoint configuration, used only on initialization. e.g. URI
     */
    @XNodeMap(value = "config", key = "@name", type = HashMap.class, componentType = String.class)
    public Map<String, String> configuration = new HashMap<>();

    /**
     * Default options to send with the request
     */
    @XNodeMap(value = "option", key = "@name", type = HashMap.class, componentType = String.class)
    public Map<String, String> defaultOptions = new HashMap<>();

    /**
     * Information about the model, e.g. name, training reference
     */
    @XNodeMap(value = "info", key = "@name", type = HashMap.class, componentType = String.class)
    public Map<String, String> info = new HashMap<>();

    @XNode("filter")
    public ModelPredicate filter;

    @XNode("@class")
    protected Class<? extends RuntimeModel> clazz;

    /**
     * Get a runtime model
     */
    public RuntimeModel getModel() {
        try {
            if (clazz == null) {
                clazz = DEFAULT_MODEL_CLASS;
            }
            RuntimeModel model = clazz.getDeclaredConstructor().newInstance();
            model.init(this);
            return model;
        } catch (ReflectiveOperationException e) {
            throw new NuxeoException(String.format("ModelDescriptor for %s is invalid.", id), e);
        }
    }

    /**
     * Get a Predicate to test before using the model
     */
    public Predicate<DocumentModel> getPredicate() {
        Predicate<DocumentModel> predicate = Predicates.doc();
        if (filter != null && StringUtils.isNotBlank(filter.primaryType)) {
            predicate = predicate.and(d -> filter.primaryType.equals(d.getType()));
        }
        return predicate.and(d -> inputs.stream().allMatch(i -> notNull(d, i.name)));
    }

    @XObject("filter")
    public static class ModelPredicate {

        @XNode("@primaryType")
        String primaryType;
    }

    @XObject("input")
    public static class Property {

        @XNode("@name")
        String name;

        @XNode("@type")
        String type;

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Property{");
            sb.append("name='").append(name).append('\'');
            sb.append(", type='").append(type).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }
}
