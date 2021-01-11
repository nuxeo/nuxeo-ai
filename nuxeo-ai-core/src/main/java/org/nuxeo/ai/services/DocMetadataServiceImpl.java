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

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AIConstants.AUTO;
import org.nuxeo.ai.auto.AutoHistory;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
import org.nuxeo.ecm.platform.audit.api.ExtendedInfo;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.ecm.platform.audit.impl.ExtendedInfoImpl;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_INPUT_DOCPROP_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_ITEMS;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_MODEL;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_SCHEMA_NAME;
import static org.nuxeo.ai.AIConstants.SUGGESTION_PROPERTY;
import static org.nuxeo.ai.AIConstants.SUGGESTION_SUGGESTIONS;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;
import static org.nuxeo.ecm.core.event.impl.DocumentEventContext.COMMENT_PROPERTY_KEY;

/**
 * An implementation of DocMetadataService
 */
public class DocMetadataServiceImpl extends DefaultComponent implements DocMetadataService {

    public static final String ENRICHMENT_ADDED = "ENRICHMENT_ADDED";

    protected static final TypeReference<List<AutoHistory>> HISTORY_TYPE = new TypeReference<List<AutoHistory>>() {
    };

    private static final Logger log = LogManager.getLogger(DocMetadataServiceImpl.class);

    /**
     * Have one of the supplied properties been modified?
     */
    public static boolean hadBeenModified(DocumentModel doc, Set<String> props) {

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
    public DocumentModel saveEnrichment(CoreSession session, EnrichmentMetadata metadata) {
        // TODO: Handle versions here?
        DocumentModel doc;
        try {
            doc = session.getDocument(new IdRef(metadata.context.documentRef));
        } catch (DocumentNotFoundException e) {
            log.info("Unable to save enrichment data for missing doc " + metadata.context.documentRef);
            return null;
        }

        Map<String, Object> anItem = metadata.toMap();
        if (anItem != null) {
            if (!doc.hasFacet(ENRICHMENT_FACET)) {
                doc.addFacet(ENRICHMENT_FACET);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> enrichmentList = (List<Map<String, Object>>) doc.getProperty(
                    ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS);
            if (enrichmentList == null) {
                enrichmentList = new ArrayList<>(1);
            }
            Collection<Map<String, Object>> allEnriched = updateEnrichment(enrichmentList, anItem);
            doc.setProperty(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS, allEnriched);
            doc.putContextData(ENRICHMENT_ADDED, Boolean.TRUE);
            raiseEvent(doc, ENRICHMENT_MODIFIED, null, metadata.getModelName());
        }
        return doc;
    }

    /**
     * Updates enrichment, ensures we have one enrichment entry per model/version and input
     */
    protected Collection<Map<String, Object>> updateEnrichment(List<Map<String, Object>> original,
            Map<String, Object> item) {
        Map<String, Map<String, Object>> enrichmentByKey = new HashMap<>();
        original.forEach(o -> enrichmentByKey.put(uniqueKey(o), o));
        enrichmentByKey.put(uniqueKey(item), item);
        return enrichmentByKey.values();
    }

    /**
     * Generate a unique key for a model/version/input combination
     */
    @SuppressWarnings("unchecked")
    protected String uniqueKey(Map<String, Object> suggestion) {
        String input = "";
        Object inputs = suggestion.get(ENRICHMENT_INPUT_DOCPROP_PROPERTY);
        // This is a little big strange, but it adapts to the type and calls the correct join method.
        if (inputs instanceof Set) {
            input = String.join(";", (Set<String>) inputs);
        } else if (inputs instanceof String[]) {
            input = String.join(";", (String[]) inputs);
        }
        return suggestion.get(ENRICHMENT_MODEL) + input;
    }

    @Override
    public DocumentModel updateAuto(DocumentModel doc, AUTO autoField, String xPath, String model,
            Serializable oldValue, String comment) {
        if (!doc.hasFacet(ENRICHMENT_FACET)) {
            doc.addFacet(ENRICHMENT_FACET);
        }

        Set<Map<String, String>> autoProps = getAutoPropAsSet(doc, autoField.lowerName());
        HashMap<String, String> prediction = new HashMap<>();
        prediction.put("xpath", xPath);
        prediction.put("model", model);
        autoProps.add(prediction);
        doc.setProperty(ENRICHMENT_SCHEMA_NAME, autoField.lowerName(), autoProps);
        doc.putContextData(ENRICHMENT_ADDED, Boolean.TRUE);

        if (oldValue != null) {
            List<AutoHistory> existingHistory = getAutoHistory(doc);
            // First remove old history if it exists
            List<AutoHistory> history = existingHistory.stream()
                                                       .filter(h -> !xPath.equals(h.getProperty()))
                                                       .collect(Collectors.toList());
            history.add(new AutoHistory(xPath, oldValue));
            setAutoHistory(doc, history);
        }

        raiseEvent(doc, autoField.eventName(), Collections.singleton(xPath), comment);

        storeAudit(doc, autoField, model, 1L, comment);

        return doc;
    }

    @Override
    public DocumentModel resetAuto(DocumentModel doc, AUTO autoField, String xPath, boolean resetValue) {
        List<AutoHistory> history = getAutoHistory(doc);
        Optional<AutoHistory> previous = history.stream().filter(h -> xPath.equals(h.getProperty())).findFirst();
        boolean present = previous.isPresent();
        Set<Map<String, String>> set = getAutoPropAsSet(doc, autoField.lowerName());
        Set<Map<String, String>> toReset = set.stream()
                                              .filter(val -> val.get("xpath").equals(xPath))
                                              .collect(Collectors.toSet());
        @SuppressWarnings("unchecked")
        Collection<Map<String, String>> noOldXpath = CollectionUtils.disjunction(set, toReset);
        Object previousValue = null;
        if (set.size() > noOldXpath.size()) {
            if (present) {
                previousValue = previous.get().getPreviousValue();
                history.remove(previous.get());
                setAutoHistory(doc, history);
            }
            //Set the value
            doc.setProperty(ENRICHMENT_SCHEMA_NAME, autoField.lowerName(), noOldXpath);
            String comment = "Resetting " + xPath + " property";
            toReset.forEach(map -> {
                storeAudit(doc, autoField, map.get("model"), -1L, comment);
            });

            if (resetValue) {
                doc.setPropertyValue(xPath, (Serializable) previousValue);
            }
        }

        return doc;
    }

    protected Set<Map<String, String>> getAutoPropAsSet(DocumentModel doc, String autoPropertyName) {
        Set<Map<String, String>> autoProps = new HashSet<>(1);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> filled = (List<Map<String, String>>) doc.getProperty(ENRICHMENT_SCHEMA_NAME,
                autoPropertyName);
        if (filled != null) {
            autoProps.addAll(filled);
        }

        return autoProps;
    }

    protected void storeAudit(DocumentModel doc, AUTO autoField, String model, long value, String comment) {
        AuditLogger audit = Framework.getService(AuditLogger.class);
        if (audit != null) {
            LogEntry logEntry = audit.newLogEntry();
            logEntry.setCategory("AI");
            logEntry.setEventId(autoField.eventName());
            logEntry.setComment(comment);
            logEntry.setDocUUID(doc.getId());
            logEntry.setDocPath(doc.getPathAsString());
            logEntry.setEventDate(new Date());

            ExtendedInfoImpl.StringInfo modelInfo = new ExtendedInfoImpl.StringInfo(model);
            ExtendedInfoImpl.LongInfo one = new ExtendedInfoImpl.LongInfo(value);

            HashMap<String, ExtendedInfo> infos = new HashMap<>();
            infos.put("model", modelInfo);
            infos.put("value", one);
            logEntry.setExtendedInfos(infos);

            audit.addLogEntries(Collections.singletonList(logEntry));
        } else {
            log.warn("Audit Logger is not available");
        }
    }

    protected void raiseEvent(DocumentModel doc, String eventName, Set<String> xPaths, String comment) {
        DocumentEventContext ctx = new DocumentEventContext(doc.getCoreSession(), doc.getCoreSession().getPrincipal(),
                doc);
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
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsList = (List<Map<String, Object>>) doc.getProperty(ENRICHMENT_SCHEMA_NAME,
                ENRICHMENT_ITEMS);
        if (itemsList == null) {
            return doc;
        }
        List<Map<String, Object>> newSuggestList = new ArrayList<>(itemsList.size());

        itemsList.forEach(suggestObj -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>) suggestObj.get(SUGGESTION_SUGGESTIONS);
            List<Map<String, Object>> newSuggestions = suggestions.stream()
                                                                  .filter(s -> !xPath.equals(
                                                                          s.get(SUGGESTION_PROPERTY)))
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
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsList = (List<Map<String, Object>>) doc.getProperty(ENRICHMENT_SCHEMA_NAME,
                ENRICHMENT_ITEMS);
        if (itemsList == null || itemsList.isEmpty()) {
            return doc;
        }
        List<Map<String, Object>> cleanItemsList = new ArrayList<>(itemsList.size());
        Set<String> removedTargetProperties = new HashSet<>();

        itemsList.forEach(entry -> {
            String[] props = (String[]) entry.get(ENRICHMENT_INPUT_DOCPROP_PROPERTY);
            Set<String> inputProperties = props == null ? Collections.emptySet() : new HashSet<>(Arrays.asList(props));
            if (hadBeenModified(doc, inputProperties)) {
                @SuppressWarnings("unchecked")
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

    @Override
    public List<AutoHistory> getAutoHistory(DocumentModel doc) {
        try {
            Blob autoBlob = (Blob) doc.getProperty(ENRICHMENT_SCHEMA_NAME, AUTO.HISTORY.lowerName());
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

            String autoHistory = AUTO.HISTORY.lowerName();
            autoBlob.setFilename(autoHistory + "_" + doc.getName() + ".json");
            doc.setProperty(ENRICHMENT_SCHEMA_NAME, autoHistory, autoBlob);
        } catch (IOException e) {
            log.warn("Failed to set auto history blob", e);
        }
    }
}
