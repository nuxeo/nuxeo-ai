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

import static org.nuxeo.ai.AIConstants.AUTO_CORRECTED;
import static org.nuxeo.ai.AIConstants.AUTO_FILLED;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_ITEMS;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_MODEL;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_MODEL_VERSION;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_SCHEMA_NAME;
import static org.nuxeo.ai.AIConstants.SUGGESTION_CONFIDENCE;
import static org.nuxeo.ai.AIConstants.SUGGESTION_LABEL;
import static org.nuxeo.ai.AIConstants.SUGGESTION_LABELS;
import static org.nuxeo.ai.AIConstants.SUGGESTION_PROPERTY;
import static org.nuxeo.ai.AIConstants.SUGGESTION_SUGGESTIONS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * An adapter for suggestion metadata.
 */
public class SuggestionMetadataAdapter {

    protected final DocumentModel doc;

    protected Set<String> models = new HashSet<>();

    protected Set<String> autoFilled = new HashSet<>();

    protected Set<String> autoCorrected = new HashSet<>();

    protected Map<String, List<LabelSuggestion>> suggestionsByModelId = new HashMap<>();

    protected Map<String, List<AIMetadata.Label>> suggestionsByProperty = new HashMap<>();

    public SuggestionMetadataAdapter(DocumentModel doc) {
        this.doc = doc;
        adaptToDoc();
    }

    public static String modelKey(String modelName, String modelVersion) {
        return modelName + ";" + (modelVersion != null ? modelVersion : "");
    }

    protected void adaptToDoc() {

        if (!doc.hasFacet(ENRICHMENT_FACET)) {
            return;
        }

        String[] autoVals = (String[]) doc.getProperty(ENRICHMENT_SCHEMA_NAME, AUTO_FILLED);
        if (autoVals != null) {
            autoFilled.clear();
            autoFilled.addAll(Arrays.asList(autoVals));
        }

        autoVals = (String[]) doc.getProperty(ENRICHMENT_SCHEMA_NAME, AUTO_CORRECTED);
        if (autoVals != null) {
            autoCorrected.clear();
            autoCorrected.addAll(Arrays.asList(autoVals));
        }

        List<Map<String, Object>> suggestList = (List) doc.getProperty(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS);
        if (suggestList == null) {
            return;
        }

        suggestList.forEach(suggestObj -> {
            String modelName = (String) suggestObj.get(ENRICHMENT_MODEL);
            String modelVersion = (String) suggestObj.get(ENRICHMENT_MODEL_VERSION);
            String modelId = modelKey(modelName, modelVersion);
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>) suggestObj.get(SUGGESTION_SUGGESTIONS);

            for (Map<String, Object> suggestion : suggestions) {
                String property = (String) suggestion.get(SUGGESTION_PROPERTY);
                List<Map<String, Object>> values = (List<Map<String, Object>>) suggestion.get(SUGGESTION_LABELS);
                List<AIMetadata.Label> labels = values.stream()
                                                      .map(v -> new AIMetadata.Label((String) v
                                                              .get(SUGGESTION_LABEL), ((Double) v
                                                              .get(SUGGESTION_CONFIDENCE)).floatValue()))
                                                      .collect(Collectors.toList());
                LabelSuggestion label = new LabelSuggestion(property, labels);
                models.add(modelName);
                List<LabelSuggestion> byModel = suggestionsByModelId.getOrDefault(modelId, new ArrayList<>());
                byModel.add(label);
                suggestionsByModelId.put(modelId, byModel);
                List<AIMetadata.Label> byProperty = suggestionsByProperty.getOrDefault(property, new ArrayList<>());
                byProperty.addAll(labels);
                suggestionsByProperty.put(property, byProperty);
            }

        });
    }

    public DocumentModel getDoc() {
        return doc;
    }

    public Set<String> getModels() {
        return models;
    }

    public List<LabelSuggestion> getSuggestionsByModel(String modelName, String modelVersion) {
        return suggestionsByModelId.getOrDefault(modelKey(modelName, modelVersion), Collections.emptyList());
    }

    public List<AIMetadata.Label> getSuggestionsByProperty(String propertyName) {
        return suggestionsByProperty.getOrDefault(propertyName, Collections.emptyList());
    }

    public Set<String> getSuggestedProperties() {
        return suggestionsByProperty.keySet();
    }

    public boolean isAutoFilled(String propertyName) {
        return autoFilled.contains(propertyName);
    }

    public boolean isAutoCorrected(String propertyName) {
        return autoCorrected.contains(propertyName);
    }

    /**
     * The property is not null and it was not auto-filled or auto-corrected.
     *
     * @param propertyName the property to check
     * @return true if the value was entered by a human.
     */
    public boolean hasHumanValue(String propertyName) {
        //auto-filled  //auto-correct
        return false;
    }
}
