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

import static java.util.Collections.emptyList;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.getBlobFromProvider;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nuxeo.ai.enrichment.AbstractEnrichmentProvider;
import org.nuxeo.ai.enrichment.EnrichmentCachable;
import org.nuxeo.ai.enrichment.EnrichmentDescriptor;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.hyland.content.intelligence.service.enrichment.HylandKEService;
import org.nuxeo.runtime.api.Framework;

/**
 * Enrichment provider that delegates to {@link HylandKEService} to call the Hyland Content Intelligence
 * Knowledge Enrichment API (presigned URL, upload, process, pull results) and maps the wrapped JSON
 * response into {@link EnrichmentMetadata}.
 */
public class ContentIntelligenceEnrichmentProvider extends AbstractEnrichmentProvider implements EnrichmentCachable {

    public static final String OPTION_CONFIG_NAME = "configName";

    public static final String OPTION_ACTIONS = "actions";

    public static final String OPTION_CLASSES = "classes";

    public static final String OPTION_SIMILAR_METADATA = "similarMetadata";

    public static final String OPTION_EXTRA_JSON_PAYLOAD = "extraJsonPayload";

    public static final String DEFAULT_CONFIG_NAME = "default";

    public static final String DEFAULT_ACTIONS = "image-description";

    /**
     * Hyland CI action keys whose result is a long-form description / summary. These never become tags; they are
     * persisted to {@code dc:description} by {@link ContentIntelligenceDescriptionListener} via the raw blob, and
     * are deliberately omitted from the {@link LabelSuggestion} list so that any downstream tag-writer
     * (e.g. {@code StoreLabelsAsTags} when {@code nuxeo.enrichment.save.tags=true}) cannot turn the description
     * paragraph into a giant tag.
     */
    public static final Set<String> DESCRIPTION_ACTION_KEYS = Set.of("imageDescription", "textSummary");

    /** Registered name of the image-pipeline enrichment provider (see {@code ai-content-intelligence-config.xml.nxftl}). */
    public static final String IMAGE_PROVIDER_NAME = "ai.contentintelligence";

    /** Registered name of the document-pipeline enrichment provider (see {@code ai-content-intelligence-config.xml.nxftl}). */
    public static final String DOCUMENTS_PROVIDER_NAME = "ai.contentintelligence.documents";

    private static final Log log = LogFactory.getLog(ContentIntelligenceEnrichmentProvider.class);

    protected String configName;

    protected List<String> actions;

    protected List<String> classes;

    protected String similarMetadata;

    protected String extraJsonPayload;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        configName = descriptor.options.getOrDefault(OPTION_CONFIG_NAME, DEFAULT_CONFIG_NAME);
        actions = splitCsv(descriptor.options.getOrDefault(OPTION_ACTIONS, DEFAULT_ACTIONS));
        if (actions.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("%s must declare at least one Knowledge Enrichment action", descriptor.name));
        }
        classes = splitCsv(descriptor.options.get(OPTION_CLASSES));
        similarMetadata = StringUtils.trimToNull(descriptor.options.get(OPTION_SIMILAR_METADATA));
        extraJsonPayload = StringUtils.trimToNull(descriptor.options.get(OPTION_EXTRA_JSON_PAYLOAD));
    }

    protected List<String> splitCsv(String value) {
        if (StringUtils.isBlank(value)) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                     .map(String::trim)
                     .filter(StringUtils::isNotBlank)
                     .toList();
    }

    /**
     * Resolves the backing storage blob for a {@link org.nuxeo.ecm.core.blob.ManagedBlob}. Overridable for testing.
     */
    protected Blob resolveBlob(org.nuxeo.ecm.core.blob.ManagedBlob managedBlob) {
        return getBlobFromProvider(managedBlob);
    }

    /**
     * Resolves the {@link HylandKEService}. Overridable for testing.
     */
    protected HylandKEService getService() {
        HylandKEService service = Framework.getService(HylandKEService.class);
        if (service == null) {
            throw new NuxeoException(
                    "HylandKEService is not available; ensure nuxeo-content-intelligence-connector-core is deployed");
        }
        return service;
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {
        if (blobTextFromDoc.getBlobs().isEmpty()) {
            return emptyList();
        }
        HylandKEService service = getService();

        List<EnrichmentMetadata> enriched = new ArrayList<>();
        for (Map.Entry<String, org.nuxeo.ecm.core.blob.ManagedBlob> entry : blobTextFromDoc.getBlobs().entrySet()) {
            String xPath = entry.getKey();
            Blob blob = resolveBlob(entry.getValue());
            if (blob == null) {
                log.warn("Could not resolve blob for property " + xPath);
                continue;
            }

            ServiceCallResult result;
            try {
                result = service.enrich(configName, blob, actions, classes, similarMetadata, extraJsonPayload);
            } catch (IOException e) {
                throw new NuxeoException(
                        "Knowledge Enrichment call failed for " + blobTextFromDoc.getId() + "/" + xPath, e);
            }

            if (result == null || result.callFailed()) {
                int code = result == null ? -1 : result.getResponseCode();
                String msg = result == null ? "null result" : result.getResponseMessage();
                String body = result == null ? "" : StringUtils.defaultString(result.getResponse());
                log.warn(String.format(
                        "Knowledge Enrichment call unsuccessful (status=%d, message=%s) for %s/%s - body: %s",
                        code, msg, blobTextFromDoc.getId(), xPath, body));
                continue;
            }

            enriched.addAll(processResponse(blobTextFromDoc, xPath, result));
        }
        return enriched;
    }

    /**
     * Parses the wrapped Knowledge Enrichment response and turns each {@code results[]} entry into an
     * {@link EnrichmentMetadata} bundle.
     */
    protected Collection<EnrichmentMetadata> processResponse(BlobTextFromDocument blobTextFromDoc, String xPath,
            ServiceCallResult result) {
        JSONObject response;
        try {
            response = result.getResponseAsJSONObject();
        } catch (JSONException e) {
            log.warn("Knowledge Enrichment response is not a JSON object: " + e.getMessage());
            return emptyList();
        }

        String status = response.optString("status", "");
        if (!status.isEmpty() && !"SUCCESS".equalsIgnoreCase(status) && !"PARTIAL_SUCCESS".equalsIgnoreCase(status)
                && !"PARTIAL_FAILURE".equalsIgnoreCase(status)) {
            log.warn("Knowledge Enrichment returned status '" + status + "', skipping. Response body: "
                    + result.toJsonString());
            return emptyList();
        }

        JSONArray results = response.optJSONArray("results");
        if (results == null || results.isEmpty()) {
            return emptyList();
        }

        String rawKey = saveJsonAsRawBlob(result.toJsonString());

        List<EnrichmentMetadata> metadata = new ArrayList<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject entry = results.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            List<LabelSuggestion> suggestions = toLabelSuggestions(entry);
            if (suggestions.isEmpty()) {
                continue;
            }
            metadata.add(new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc).withLabels(suggestions)
                                                                                    .withRawKey(rawKey)
                                                                                    .withDocumentProperties(
                                                                                            Collections.singleton(xPath))
                                                                                    .build());
        }
        return metadata;
    }

    /**
     * Converts each successful action result block ({@code imageClassification}, {@code namedEntityImage},
     * {@code textClassification}, ...) into a {@link LabelSuggestion} whose {@link LabelSuggestion#getProperty()
     * property} is the Hyland action key and whose values are the plain extracted strings (no {@code action/} prefix).
     * <p>
     * Action keys listed in {@link #DESCRIPTION_ACTION_KEYS} ({@code imageDescription}, {@code textSummary}) are
     * deliberately skipped: their result is a long-form paragraph that does not belong in the labels stream. It is
     * persisted to {@code dc:description} out-of-band by {@link ContentIntelligenceDescriptionListener}, which reads
     * the raw JSON blob.
     * <p>
     * Supported {@code result} shapes:
     * <ul>
     *   <li>{@code String}: one label.</li>
     *   <li>{@code JSONArray} of strings: one label per entry.</li>
     *   <li>{@code JSONObject} of {@code String -> (String | JSONArray&lt;String&gt;)}: flattens each entry, useful for
     *       entity buckets like {@code {"locations":["New York"],"persons":["Jane"]}}.</li>
     * </ul>
     * Numeric embedding arrays (e.g. {@code imageEmbeddings}) are preserved only in the raw blob.
     */
    protected List<LabelSuggestion> toLabelSuggestions(JSONObject entry) {
        Map<String, List<AIMetadata.Label>> labelsByAction = new LinkedHashMap<>();
        for (String key : entry.keySet()) {
            if ("objectKey".equals(key) || DESCRIPTION_ACTION_KEYS.contains(key)) {
                continue;
            }
            JSONObject action = entry.optJSONObject(key);
            if (action == null || !action.optBoolean("isSuccess", false) || !action.has("result")) {
                continue;
            }
            List<AIMetadata.Label> labels = labelsByAction.computeIfAbsent(key, k -> new ArrayList<>());
            collectFromResult(labels, action.get("result"));
        }
        List<LabelSuggestion> suggestions = new ArrayList<>(labelsByAction.size());
        for (Map.Entry<String, List<AIMetadata.Label>> e : labelsByAction.entrySet()) {
            if (!e.getValue().isEmpty()) {
                suggestions.add(new LabelSuggestion(e.getKey(), e.getValue()));
            }
        }
        return suggestions;
    }

    /**
     * Recursively flattens a Hyland CI {@code result} value into clean-name labels. Strings become a single label,
     * {@link JSONArray} entries are visited one by one (any non-string member is ignored to avoid picking up
     * numeric vectors), and {@link JSONObject} entries are visited value-by-value. The label name carries only the
     * extracted value (no action prefix); the action is carried by the enclosing {@link LabelSuggestion#getProperty()}.
     */
    protected void collectFromResult(List<AIMetadata.Label> labels, Object raw) {
        if (raw instanceof String value) {
            if (StringUtils.isNotBlank(value)) {
                labels.add(new AIMetadata.Label(value.trim(), 1.0F));
            }
            return;
        }
        if (raw instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                Object item = array.get(i);
                if (item instanceof String || item instanceof JSONArray || item instanceof JSONObject) {
                    collectFromResult(labels, item);
                }
            }
            return;
        }
        if (raw instanceof JSONObject object) {
            for (String childKey : object.keySet()) {
                collectFromResult(labels, object.get(childKey));
            }
        }
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }

}
