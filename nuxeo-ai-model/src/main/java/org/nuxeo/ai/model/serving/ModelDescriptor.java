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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.nuxeo.ai.model.ModelProperty;
import org.nuxeo.ai.pipes.functions.Predicates;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Definition of an AI model at runtime
 */
@XObject("model")
public class ModelDescriptor {

    private static final Class<? extends RuntimeModel> DEFAULT_MODEL_CLASS = TFRuntimeModel.class;

    @XNode("@id")
    public String id;

    /**
     * Endpoint configuration, used only on initialization. e.g. URI
     */
    @XNodeMap(value = "config", key = "@name", type = HashMap.class, componentType = String.class)
    @JsonIgnore
    public Map<String, String> configuration = new HashMap<>();

    /**
     * Information about the model, e.g. name, training reference
     */
    @XNodeMap(value = "info", key = "@name", type = HashMap.class, componentType = String.class)
    public Map<String, String> info = new HashMap<>();

    @XNode("filter")
    public DocumentPredicate filter;

    @XNode("inputProperties")
    protected InputProperties inputProperties;

    @XNode("outputProperties")
    protected OutputProperties outputProperties;

    @XNode("@class")
    @JsonIgnore
    protected Class<? extends RuntimeModel> clazz;

    public Set<ModelProperty> getInputs() {
        return inputProperties.properties;
    }

    public Set<ModelProperty> getOutputs() {
        return outputProperties.properties;
    }

    /**
     * Get a runtime model
     */
    @JsonIgnore
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

    @XObject("filter")
    public static class DocumentPredicate implements Supplier<Predicate<DocumentModel>> {

        @XNode("@primaryType")
        @JsonProperty
        String primaryType;

        @Override
        public Predicate<DocumentModel> get() {
            return Predicates.isType(primaryType);
        }
    }

    @XObject("inputProperties")
    public static class InputProperties {
        @XNodeList(value = "property", type = HashSet.class, componentType = ModelProperty.class)
        protected Set<ModelProperty> properties = new HashSet<>();
    }

    @XObject("outputProperties")
    public static class OutputProperties {
        @XNodeList(value = "property", type = HashSet.class, componentType = ModelProperty.class)
        protected Set<ModelProperty> properties = new HashSet<>();
    }

}
