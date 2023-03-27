/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.metadata;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_ITEMS;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_MODEL;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_SCHEMA_NAME;
import static org.nuxeo.ai.AIConstants.SUGGESTION_CONFIDENCE;
import static org.nuxeo.ai.AIConstants.SUGGESTION_LABEL;
import static org.nuxeo.ai.AIConstants.SUGGESTION_LABELS;
import static org.nuxeo.ai.AIConstants.SUGGESTION_PROPERTY;
import static org.nuxeo.ai.AIConstants.SUGGESTION_SUGGESTIONS;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AIConstants.AUTO;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;

/**
 * An wrapper around suggestion metadata that pre-processes the data to make it easier to use.
 */
public class SuggestionMetadataWrapper {

    private static final Logger log = LogManager.getLogger(SuggestionMetadataWrapper.class);

    protected final DocumentModel doc;

    protected Set<String> models = new HashSet<>();

    protected Set<Map<String, String>> autoFilled = new HashSet<>();

    protected Set<Map<String, String>> autoCorrected = new HashSet<>();

    protected Set<String> autoProperties = new HashSet<>();

    protected Map<String, List<LabelSuggestion>> suggestionsByModelId = new HashMap<>();

    public SuggestionMetadataWrapper(DocumentModel doc) {
        this.doc = doc;
        init();
    }

    /**
     * Process the document
     */
    @SuppressWarnings("unchecked")
    protected void init() {
        if (!doc.hasFacet(ENRICHMENT_FACET)) {
            return;
        }

        List<Map<String, String>> filled = (List<Map<String, String>>) doc.getProperty(ENRICHMENT_SCHEMA_NAME,
                AUTO.FILLED.lowerName());
        if (filled != null) {
            autoFilled.clear();
            autoFilled.addAll(filled);
        }

        List<Map<String, String>> corrected = (List<Map<String, String>>) doc.getProperty(ENRICHMENT_SCHEMA_NAME,
                AUTO.CORRECTED.lowerName());
        if (corrected != null) {
            autoCorrected.clear();
            autoCorrected.addAll(corrected);
        }

        List<Map<String, Object>> suggestList = (List<Map<String, Object>>) doc.getProperty(ENRICHMENT_SCHEMA_NAME,
                ENRICHMENT_ITEMS);
        if (suggestList == null) {
            return;
        }

        suggestList.forEach(suggestObj -> {
            String modelId = (String) suggestObj.get(ENRICHMENT_MODEL);
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>) suggestObj.get(SUGGESTION_SUGGESTIONS);

            for (Map<String, Object> suggestion : suggestions) {
                String property = (String) suggestion.get(SUGGESTION_PROPERTY);
                List<Map<String, Object>> values = (List<Map<String, Object>>) suggestion.get(SUGGESTION_LABELS);
                List<AIMetadata.Label> labels = values.stream()
                                                      .map(v -> new AIMetadata.Label((String) v.get(SUGGESTION_LABEL),
                                                              ((Double) v.get(SUGGESTION_CONFIDENCE)).floatValue(), 0L))
                                                      .collect(Collectors.toList());
                LabelSuggestion label = new LabelSuggestion(property, labels);
                models.add(modelId);
                List<LabelSuggestion> byModel = suggestionsByModelId.getOrDefault(modelId, new ArrayList<>());
                byModel.add(label);

                suggestionsByModelId.put(modelId, byModel);
            }
        });

        suggestionsByModelId.values()
                            .stream()
                            .flatMap(Collection::stream)
                            .map(val -> val.property)
                            .filter(Objects::nonNull)
                            .forEach(autoProperties::add);

        Stream.concat(autoFilled.stream(), autoCorrected.stream())
              .map(val -> val.get("xpath"))
              .filter(Objects::nonNull)
              .forEach(autoProperties::add);
    }

    public DocumentModel getDoc() {
        return doc;
    }

    public Set<String> getModels() {
        return models;
    }

    public List<LabelSuggestion> getSuggestionsByModel(String modelId) {
        return suggestionsByModelId.getOrDefault(modelId, Collections.emptyList());
    }

    public List<PropertyHolder> getSuggestionsByProperty(String propertyName) {
        //        return suggestionsByProperty.getOrDefault(propertyName, Collections.emptyList());
        List<PropertyHolder> list = new ArrayList<>();
        for (String key : suggestionsByModelId.keySet()) {
            for (LabelSuggestion labelSuggestion : suggestionsByModelId.get(key)) {
                if (propertyName.equals(labelSuggestion.property)) {
                    list.add(PropertyHolder.of(key, labelSuggestion.values));
                }
            }
        }

        return list;
    }

    /**
     * Get the xPath of any properties that could be suggested or have been auto filled or auto corrected.
     */
    public Set<String> getAutoProperties() {
        return autoProperties;
    }

    public Set<Map<String, String>> getAutoFilled() {
        return autoFilled;
    }

    /**
     * Update auto filled values for sequential operations
     *
     * @param xpath {@link DocumentModel} property path
     * @return {@link Boolean#TRUE} if successfully added
     */
    public boolean addAutoFilled(String xpath, String model) {
        Map<String, String> props = new HashMap<>();
        props.put("xpath", xpath);
        props.put("model", model);
        return autoFilled.add(props);
    }

    public Set<Map<String, String>> getAutoCorrected() {
        return autoCorrected;
    }

    public boolean isAutoFilled(String propertyName) {
        return autoFilled.stream().map(filled -> filled.get("xpath")).anyMatch(xpath -> xpath.equals(propertyName));
    }

    public boolean isAutoCorrected(String propertyName) {
        return autoCorrected.stream().map(filled -> filled.get("xpath")).anyMatch(xpath -> xpath.equals(propertyName));
    }

    /**
     * Indicates if a property has a value.
     */
    public boolean hasValue(String xpath) {
        try {
            Property property = doc.getProperty(xpath);
            Serializable propValue = property.getValue();
            if (propValue == null) {
                return false;
            }
            Type propertyType = property.getType();
            if (StringType.INSTANCE.equals(propertyType)) {
                return isNotEmpty((String) propValue);
            } else if (property.isList()) {
                if (propValue instanceof Object[]) {
                    return ((Object[]) propValue).length != 0;
                } else {
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) propValue;
                    return !list.isEmpty();
                }
            }
            return true;
        } catch (PropertyNotFoundException e) {
            log.warn("Unknown auto property {} ", xpath);
            return true;
        }
    }

    /**
     * The property is not null and it was not auto-filled or auto-corrected.
     *
     * @param propertyName the property to check
     * @return true if the value was entered by a human.
     */
    public boolean hasHumanValue(String propertyName) {
        return hasValue(propertyName) && !(isAutoFilled(propertyName) || isAutoCorrected(propertyName));
    }

    public static class PropertyHolder {

        protected String model;

        protected List<AIMetadata.Label> labels;

        protected PropertyHolder(String model, List<AIMetadata.Label> labels) {
            this.model = model;
            this.labels = labels;
        }

        public static PropertyHolder of(String model, List<AIMetadata.Label> labels) {
            return new PropertyHolder(model, labels);
        }

        public String getModel() {
            return model;
        }

        public List<AIMetadata.Label> getLabels() {
            return labels;
        }
    }
}
