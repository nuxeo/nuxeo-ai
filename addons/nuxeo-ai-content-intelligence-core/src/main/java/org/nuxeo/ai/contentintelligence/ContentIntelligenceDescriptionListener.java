/*
 * (C) Copyright 2026 Hyland (http://hyland.com/) and others.
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
 */
package org.nuxeo.ai.contentintelligence;

import static org.nuxeo.ai.contentintelligence.ContentIntelligenceConstants.DESCRIPTION_ACTION_KEYS;
import static org.nuxeo.ai.contentintelligence.ContentIntelligenceConstants.DESCRIPTION_PROPERTY;
import static org.nuxeo.ai.contentintelligence.ContentIntelligenceConstants.ALL_PROVIDER_NAME;
import static org.nuxeo.ai.contentintelligence.ContentIntelligenceConstants.DOCUMENTS_PROVIDER_NAME;
import static org.nuxeo.ai.contentintelligence.ContentIntelligenceConstants.IMAGE_PROVIDER_NAME;
import static org.nuxeo.ai.functions.RaiseEnrichmentEvent.ENRICHMENT_METADATA;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Persists the Hyland CI long-form description ({@code imageDescription} / {@code textSummary}) to
 * {@code dc:description} when one of our enrichment providers fires an {@code enrichmentMetadataCreated} event.
 * <p>
 * The description does not travel through the {@link EnrichmentMetadata#getLabels() labels} stream on purpose: when
 * {@code nuxeo.enrichment.save.tags=true} (typical in environments where AWS / GCP / Sightengine co-exist with CIC),
 * the upstream {@code StoreLabelsAsTags} consumer would otherwise turn the description paragraph into a giant tag.
 * Instead this listener reads the raw Hyland CI JSON blob from the AI transient store and pulls the description out
 * directly.
 */
public class ContentIntelligenceDescriptionListener implements PostCommitEventListener {

    /** Provider names this listener will act on; anything else (AWS, GCP, Sightengine, Insight, ...) is ignored. */
    public static final Set<String> HANDLED_PROVIDERS = Set.of(IMAGE_PROVIDER_NAME, DOCUMENTS_PROVIDER_NAME,
            ALL_PROVIDER_NAME);

    /**
     * Framework property that caps the description length written to {@code dc:description}. Empty / zero / negative
     * values disable the cap.
     */
    public static final String MAX_LENGTH_PROP = "nuxeo.ai.contentintelligence.description.maxLength";

    private static final Logger log = LogManager.getLogger(ContentIntelligenceDescriptionListener.class);

    @Override
    public void handleEvent(EventBundle events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (Event event : events) {
            handleSingle(event);
        }
    }

    protected void handleSingle(Event event) {
        EventContext ctx = event.getContext();
        Object raw = ctx != null ? ctx.getProperty(ENRICHMENT_METADATA) : null;
        if (!(raw instanceof EnrichmentMetadata metadata)) {
            return;
        }
        if (!HANDLED_PROVIDERS.contains(metadata.getModelName()) || metadata.context == null
                || StringUtils.isBlank(metadata.context.repositoryName)
                || StringUtils.isBlank(metadata.context.documentRef)) {
            return;
        }
        String description = extractDescription(metadata);
        if (StringUtils.isBlank(description)) {
            return;
        }
        String capped = applyMaxLength(description);
        TransactionHelper.runInTransaction(() -> CoreInstance.doPrivileged(metadata.context.repositoryName, session -> {
            DocumentModel doc;
            try {
                doc = session.getDocument(new IdRef(metadata.context.documentRef));
            } catch (DocumentNotFoundException e) {
                log.warn("Document {} no longer exists, dropping Content Intelligence description",
                        metadata.context.documentRef);
                return;
            }
            if (writeDescription(doc, capped)) {
                session.saveDocument(doc);
            }
        }));
    }

    /**
     * Writes {@code description} into {@code dc:description}, but only if the property is currently empty so that a
     * human-edited description is never silently overwritten.
     *
     * @return {@code true} if the document was mutated and needs saving
     */
    protected boolean writeDescription(DocumentModel doc, String description) {
        Object existing = doc.getPropertyValue(DESCRIPTION_PROPERTY);
        if (existing != null && StringUtils.isNotBlank(existing.toString())) {
            log.debug("dc:description already set on {}, skipping AI description", doc.getId());
            return false;
        }
        doc.setPropertyValue(DESCRIPTION_PROPERTY, description);
        return true;
    }

    /**
     * Reads the raw Hyland CI JSON blob referenced by {@code metadata.getRawKey()} from the AI transient store and
     * returns the first non-blank value under any {@link ContentIntelligenceConstants#DESCRIPTION_ACTION_KEYS
     * description action key}.
     */
    protected String extractDescription(EnrichmentMetadata metadata) {
        String rawKey = metadata.getRawKey();
        if (StringUtils.isBlank(rawKey)) {
            return null;
        }
        Blob blob;
        try {
            AIComponent aiComponent = Framework.getService(AIComponent.class);
            if (aiComponent == null) {
                return null;
            }
            TransientStore transientStore = aiComponent.getTransientStoreForEnrichmentProvider(metadata.getModelName());
            if (transientStore == null) {
                return null;
            }
            List<Blob> blobs = transientStore.getBlobs(rawKey);
            if (blobs == null || blobs.isEmpty()) {
                return null;
            }
            blob = blobs.get(0);
        } catch (RuntimeException e) {
            log.warn("Unable to read raw enrichment blob for {} / {}: {}", metadata.getModelName(), rawKey,
                    e.getMessage());
            return null;
        }

        try {
            return parseDescription(blob.getString());
        } catch (IOException | JSONException e) {
            log.warn("Unable to parse raw enrichment blob for {} / {}: {}", metadata.getModelName(), rawKey,
                    e.getMessage());
            return null;
        }
    }

    /**
     * Locates the first successful description / summary result inside the wrapped Hyland CI response payload
     * {@code {"response": {"results": [{...}]}}}. Resilient to missing wrappers and malformed entries.
     */
    protected String parseDescription(String rawJson) {
        if (StringUtils.isBlank(rawJson)) {
            return null;
        }
        JSONObject root;
        try {
            root = new JSONObject(rawJson);
        } catch (JSONException e) {
            log.warn("Malformed JSON in raw enrichment blob, cannot extract description: {}", e.getMessage());
            return null;
        }
        JSONObject response = root.optJSONObject("response");
        JSONArray results = response != null ? response.optJSONArray("results") : root.optJSONArray("results");
        if (results == null) {
            return null;
        }
        for (int i = 0; i < results.length(); i++) {
            JSONObject entry = results.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            for (String actionKey : DESCRIPTION_ACTION_KEYS) {
                JSONObject action = entry.optJSONObject(actionKey);
                if (action == null || !action.optBoolean("isSuccess", false)) {
                    continue;
                }
                Object result = action.opt("result");
                if (result instanceof String value && StringUtils.isNotBlank(value)) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    /**
     * Truncates the description to the framework-configured max length (if any) so a paragraph longer than the
     * {@code dc:description} schema constraint never aborts the listener.
     */
    protected String applyMaxLength(String description) {
        int max = readMaxLength();
        if (max <= 0 || description == null || description.length() <= max) {
            return description;
        }
        return description.substring(0, max);
    }

    protected int readMaxLength() {
        String cfg;
        try {
            cfg = Framework.getProperty(MAX_LENGTH_PROP);
        } catch (RuntimeException e) {
            // Framework may not be initialised in some contexts; treat as "no cap".
            return 0;
        }
        if (StringUtils.isBlank(cfg)) {
            return 0;
        }
        try {
            return Integer.parseInt(cfg.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid value for {}, ignoring", MAX_LENGTH_PROP);
            return 0;
        }
    }
}
