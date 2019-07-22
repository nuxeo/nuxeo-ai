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
package org.nuxeo.ai.services;

import static org.nuxeo.ai.AIConstants.AUTO_CORRECTED;
import static org.nuxeo.ai.AIConstants.AUTO_HISTORY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_INPUT_DOCPROP_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_ITEMS;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_MODEL;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_MODEL_VERSION;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_RAW_KEY_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_SCHEMA_NAME;
import static org.nuxeo.ai.AIConstants.NORMALIZED_PROPERTY;
import static org.nuxeo.ai.AIConstants.SUGGESTION_CONFIDENCE;
import static org.nuxeo.ai.AIConstants.SUGGESTION_LABEL;
import static org.nuxeo.ai.AIConstants.SUGGESTION_LABELS;
import static org.nuxeo.ai.AIConstants.SUGGESTION_PROPERTY;
import static org.nuxeo.ai.AIConstants.SUGGESTION_SUGGESTIONS;
import static org.nuxeo.ai.metadata.SuggestionMetadataAdapter.modelKey;
import static org.nuxeo.ai.pipes.events.DirtyEventListener.DIRTY_EVENT_NAME;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;
import static org.nuxeo.ecm.core.event.impl.DocumentEventContext.COMMENT_PROPERTY_KEY;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.auto.AutoHistory;
import org.nuxeo.ai.enrichment.EnrichedEventListener;
import org.nuxeo.ai.enrichment.EnrichedPropertiesEventListener;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.pipes.services.PipelineService;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.services.config.ConfigurationService;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * An implementation of DocMetadataService
 */
public class DocMetadataServiceImpl extends DefaultComponent implements DocMetadataService {

    public static final String ENRICHMENT_ADDED = "ENRICHMENT_ADDED";

    public static final String ENRICHMENT_USING_FACETS = "nuxeo.enrichment.facets.inUse";

    protected static final TypeReference<List<AutoHistory>> HISTORY_TYPE = new TypeReference<List<AutoHistory>>() {
    };

    private static final Log log = LogFactory.getLog(DocMetadataServiceImpl.class);

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        if (Framework.getService(ConfigurationService.class).isBooleanTrue(ENRICHMENT_USING_FACETS)) {
            PipelineService pipelineService = Framework.getService(PipelineService.class);
            // Facets are being used so lets clean it up as well.
            pipelineService.addEventListener(DIRTY_EVENT_NAME, false, false, new EnrichedPropertiesEventListener());
            pipelineService.addEventListener(ENRICHMENT_MODIFIED, false, false, new EnrichedEventListener());
        }
    }

    @Override
    public DocumentModel saveEnrichment(CoreSession session, EnrichmentMetadata metadata) {
        // TODO: Handle versions here?
        DocumentModel doc;
        try {
            doc = session.getDocument(new IdRef(metadata.context.documentRef));
        } catch (DocumentNotFoundException e) {
            log.info("Unable to save enrichment data for missing doc " + metadata.context.documentRef);
            return null;
        }

        Map<String, Object> anItem = createEnrichment(metadata);

        if (anItem != null) {
            if (!doc.hasFacet(ENRICHMENT_FACET)) {
                doc.addFacet(ENRICHMENT_FACET);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> enrichmentList = (List) doc.getProperty(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS);
            if (enrichmentList == null) {
                enrichmentList = new ArrayList<>(1);
            }
            Collection allEnriched = updateEnrichment(enrichmentList, anItem);
            doc.setProperty(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS, allEnriched);
            doc.putContextData(ENRICHMENT_ADDED, Boolean.TRUE);
            raiseEvent(doc, ENRICHMENT_MODIFIED, null, metadata.getModelName());
        }
        return doc;
    }

    /**
     * Updates enrichment, ensures we have one enrichment entry per model/version and input
     */
    protected Collection<Map<String, Object>> updateEnrichment(List<Map<String, Object>> original, Map<String, Object> item) {
        Map<String, Map<String, Object>> enrichmentByKey = new HashMap<>();
        original.forEach(o -> enrichmentByKey.put(uniqueKey(o), o));
        enrichmentByKey.put(uniqueKey(item), item);
        return enrichmentByKey.values();
    }

    /**
     * Generate a unique key for a model/version/input combination
     */
    protected String uniqueKey(Map<String, Object> suggestion) {
        String input = "";
        Object inputs = suggestion.get(ENRICHMENT_INPUT_DOCPROP_PROPERTY);
        // This is a little big strange, but it adapts to the type and calls the correct join method.
        if (inputs instanceof Set) {
            input = String.join(";", (Set) inputs);
        } else if (inputs instanceof String[]) {
            input = String.join(";", (String[]) inputs);
        }
        return modelKey((String) suggestion.get(ENRICHMENT_MODEL),
                        (String) suggestion.get(ENRICHMENT_MODEL_VERSION)) + input;
    }

    /**
     * Create a enrichment Map using the enrichment metadata
     */
    protected Map<String, Object> createEnrichment(EnrichmentMetadata metadata) {

        List<Map<String, Object>> suggestions = new ArrayList<>(metadata.getLabels().size());
        metadata.getLabels().forEach(suggestion -> {
            Map<String, Object> anEntry = new HashMap<>();
            anEntry.put(SUGGESTION_PROPERTY, suggestion.getProperty());
            List<Map<String, Object>> values = new ArrayList<>(suggestion.getValues().size());
            suggestion.getValues().forEach(value -> {
                Map<String, Object> val = new HashMap<>(2);
                val.put(SUGGESTION_LABEL, value.getName());
                val.put(SUGGESTION_CONFIDENCE, value.getConfidence());
                values.add(val);
            });
            anEntry.put(SUGGESTION_LABELS, values);
            suggestions.add(anEntry);
        });

        Map<String, Object> anEntry = new HashMap<>();
        AIComponent aiComponent = Framework.getService(AIComponent.class);

        if (!suggestions.isEmpty()) {
            anEntry.put(SUGGESTION_SUGGESTIONS, suggestions);
        }

        try {
            if (StringUtils.isNotEmpty(metadata.getRawKey())) {
                TransientStore transientStore = aiComponent.getTransientStoreForEnrichmentService(
                        metadata.getModelName());
                List<Blob> rawBlobs = transientStore.getBlobs(metadata.getRawKey());
                if (rawBlobs != null && rawBlobs.size() == 1) {
                    anEntry.put(ENRICHMENT_RAW_KEY_PROPERTY, rawBlobs.get(0));
                } else {
                    log.warn(String.format("Unexpected transient store raw blob information for %s. "
                                                   + "A single raw blob is expected.", metadata.getModelName()));
                }
            }
            Blob metaDataBlob = Blobs.createJSONBlob(MAPPER.writeValueAsString(metadata));
            anEntry.put(NORMALIZED_PROPERTY, metaDataBlob);
        } catch (IOException e) {
            throw new NuxeoException("Unable to process metadata blob", e);
        }

        anEntry.put(ENRICHMENT_MODEL, metadata.getModelName());
        anEntry.put(ENRICHMENT_INPUT_DOCPROP_PROPERTY, metadata.context.inputProperties);
        if (metadata.getModelVersion() != null) {
            anEntry.put(ENRICHMENT_MODEL_VERSION, metadata.getModelVersion());
        }
        if (log.isDebugEnabled()) {
            log.debug(String.format("Enriching doc %s with %s", metadata.context.documentRef, suggestions));
        }
        return anEntry;
    }

    @Override
    public DocumentModel updateAuto(DocumentModel doc, String autoField, String xPath,
                                    Serializable oldValue, String comment) {
        if (!doc.hasFacet(ENRICHMENT_FACET)) {
            doc.addFacet(ENRICHMENT_FACET);
        }
        Set<String> autoProps = getAutoPropAsSet(doc, autoField);
        autoProps.add(xPath);
        doc.setProperty(ENRICHMENT_SCHEMA_NAME, autoField, autoProps);
        doc.putContextData(AUTO_ADDED + autoField.toUpperCase(), Boolean.TRUE);

        if (oldValue != null) {
            List<AutoHistory> existingHistory = getAutoHistory(doc);
            // First remove old history if it exists
            List<AutoHistory> history = existingHistory.stream()
                                                       .filter(h -> !xPath.equals(h.getProperty()))
                                                       .collect(Collectors.toList());
            history.add(new AutoHistory(xPath, String.valueOf(oldValue)));
            setAutoHistory(doc, history);
        }
        raiseEvent(doc, AUTO_ADDED + autoField.toUpperCase(), Collections.singleton(xPath), comment);
        return doc;
    }

    @Override
    public DocumentModel resetAuto(DocumentModel doc, String autoField, String xPath, boolean resetValue) {
        Set<String> autoProps = getAutoPropAsSet(doc, autoField);
        Serializable previousValue = null;
        if (autoProps.contains(xPath)) {
            autoProps.remove(xPath);

            if (AUTO_CORRECTED.equals(autoField)) {
                List<AutoHistory> history = getAutoHistory(doc);
                Optional<AutoHistory> previous = history.stream()
                                                        .filter(h -> xPath.equals(h.getProperty()))
                                                        .findFirst();
                if (previous.isPresent()) {
                    previousValue = previous.get().getPreviousValue();
                    history.remove(previous.get());
                    setAutoHistory(doc, history);
                }
            }
            //Set the value
            doc.setProperty(ENRICHMENT_SCHEMA_NAME, autoField, autoProps);
            if (resetValue) {
                doc.setPropertyValue(xPath, previousValue);
            }
        }
        return doc;
    }

    public Set<String> getAutoPropAsSet(DocumentModel doc, String autoPropertyName) {
        Set<String> autoProps = new HashSet<>(1);
        String[] autoVals = (String[]) doc.getProperty(ENRICHMENT_SCHEMA_NAME, autoPropertyName);
        if (autoVals != null) {
            autoProps.addAll(Arrays.asList(autoVals));
        }
        return autoProps;
    }

    protected void raiseEvent(DocumentModel doc, String eventName, Set<String> xPaths, String comment) {
        DocumentEventContext ctx = new DocumentEventContext(doc.getCoreSession(), doc.getCoreSession()
                                                                                     .getPrincipal(), doc);
        ctx.setProperty(CoreEventConstants.REPOSITORY_NAME, doc.getRepositoryName());
        ctx.setProperty(CoreEventConstants.SESSION_ID, doc.getSessionId());

        String paths = null;
        if (xPaths != null && !xPaths.isEmpty()) {
            paths = String.join(",", xPaths);
        }
        ctx.setProperty(PATHS, paths);
        if (StringUtils.isEmpty(comment)) {
            ctx.setProperty(COMMENT_PROPERTY_KEY, paths);
        } else {
            ctx.setProperty(COMMENT_PROPERTY_KEY, comment);
        }
        Framework.getService(EventService.class).fireEvent(ctx.newEvent(eventName));
    }

    @Override
    public DocumentModel removeSuggestionsForTargetProperty(DocumentModel doc, String xPath) {

        List<Map<String, Object>> itemsList = (List) doc.getProperty(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS);
        if (itemsList == null) {
            return doc;
        }
        List<Map<String, Object>> newSuggestList = new ArrayList<>(itemsList.size());

        itemsList.forEach(suggestObj -> {
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>) suggestObj.get(SUGGESTION_SUGGESTIONS);
            List<Map<String, Object>> newSuggestions = suggestions.stream()
                                                                  .filter(s -> !xPath
                                                                          .equals(s.get(SUGGESTION_PROPERTY)))
                                                                  .collect(Collectors.toList());
            if (!newSuggestions.isEmpty()) {
                suggestObj.put(SUGGESTION_SUGGESTIONS, newSuggestions);
                newSuggestList.add(suggestObj);
            }
        });
        doc.setProperty(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS, newSuggestList);
        raiseEvent(doc, ENRICHMENT_MODIFIED, Collections.singleton(xPath), SUGGESTION_SUGGESTIONS);
        return doc;
    }

    @Override
    public DocumentModel removeItemsForDirtyProperties(DocumentModel doc) {
        List<Map<String, Object>> itemsList = (List) doc.getProperty(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS);
        if (itemsList == null || itemsList.isEmpty()) {
            return doc;
        }
        List<Map<String, Object>> cleanItemsList = new ArrayList<>(itemsList.size());
        Set<String> removedTargetProperties = new HashSet<>();

        itemsList.forEach(entry -> {
            String[] props = (String[]) entry.get(ENRICHMENT_INPUT_DOCPROP_PROPERTY);
            Set<String> inputProperties = props == null ? Collections.emptySet() : new HashSet<>(Arrays.asList(props));
            if (hadBeenModified(doc, inputProperties)) {
                List<Map<String, Object>> suggestions = (List<Map<String, Object>>) entry.get(SUGGESTION_SUGGESTIONS);
                Set<String> targetProps = suggestions.stream()
                                                     .map(s -> (String) s.get(SUGGESTION_PROPERTY))
                                                     .collect(Collectors.toSet());
                removedTargetProperties.addAll(targetProps);
            } else {
                cleanItemsList.add(entry);
            }
        });

        if (cleanItemsList.size() != itemsList.size()) {
            //We made some changes lets update
            doc.setProperty(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS, cleanItemsList);
            raiseEvent(doc, ENRICHMENT_MODIFIED, removedTargetProperties, "Dirty inputs");
        }
        return doc;
    }

    /**
     * Have one of the supplied properties been modified?
     */
    protected boolean hadBeenModified(DocumentModel doc, Set<String> props) {

        if (props != null) {
            for (String propName : props) {
                try {
                    Property prop = doc.getProperty(propName);
                    if (prop != null && prop.isDirty()) {
                        return true;
                    }
                } catch (PropertyNotFoundException e) {
                    // Just ignore
                }
            }
        }
        return false;
    }

    @Override
    public List<AutoHistory> getAutoHistory(DocumentModel doc) {
        try {
            Blob autoBlob = (Blob) doc.getProperty(ENRICHMENT_SCHEMA_NAME, AUTO_HISTORY);
            if (autoBlob != null) {
                return MAPPER.readValue(autoBlob.getByteArray(), HISTORY_TYPE);
            }
        } catch (IOException e) {
            log.warn("Failed to read auto history blob", e);
        }

        return Collections.emptyList();
    }

    @Override
    public void setAutoHistory(DocumentModel doc, List<AutoHistory> history) {
        try {
            Blob autoBlob = Blobs.createJSONBlob(MAPPER.writeValueAsString(history));
            if (!doc.hasFacet(ENRICHMENT_FACET)) {
                doc.addFacet(ENRICHMENT_FACET);
            }
            doc.setProperty(ENRICHMENT_SCHEMA_NAME, AUTO_HISTORY, autoBlob);
        } catch (IOException e) {
            log.warn("Failed to set auto history blob", e);
        }
    }
}
