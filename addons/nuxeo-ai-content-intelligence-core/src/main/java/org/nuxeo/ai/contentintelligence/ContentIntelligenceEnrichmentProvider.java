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
import java.util.List;
import java.util.Map;

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
            List<AIMetadata.Label> labels = toLabels(entry);
            if (labels.isEmpty()) {
                continue;
            }
            metadata.add(new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc).withLabels(asLabels(labels))
                                                                                    .withRawKey(rawKey)
                                                                                    .withDocumentProperties(
                                                                                            Collections.singleton(xPath))
                                                                                    .build());
        }
        return metadata;
    }

    /**
     * Converts each action result block ({@code imageDescription}, {@code imageClassification},
     * {@code namedEntityImage}, {@code textSummary}, ...) into {@link AIMetadata.Label} entries. Supported shapes:
     * <ul>
     *   <li>{@code String}: single label ({@code action/value}).</li>
     *   <li>{@code JSONArray} of strings: one label per entry.</li>
     *   <li>{@code JSONObject} of {@code String -> (String | JSONArray&lt;String&gt;)}: flattens each entry, useful for
     *       entity buckets like {@code {"locations":["New York"],"persons":["Jane"]}} returned by
     *       {@code named-entity-recognition-image}.</li>
     * </ul>
     * Numeric embedding arrays (e.g. {@code imageEmbeddings}) are preserved only in the raw blob.
     */
    protected List<AIMetadata.Label> toLabels(JSONObject entry) {
        List<AIMetadata.Label> labels = new ArrayList<>();
        for (String key : entry.keySet()) {
            if ("objectKey".equals(key)) {
                continue;
            }
            JSONObject action = entry.optJSONObject(key);
            if (action == null || !action.optBoolean("isSuccess", false) || !action.has("result")) {
                continue;
            }
            collectFromResult(labels, key, action.get("result"));
        }
        return labels;
    }

    /**
     * Recursively flattens a {@code result} value into labels keyed by {@code action}. Strings become a single label,
     * {@link JSONArray} entries are visited one by one (any non-string member is ignored to avoid picking up
     * numeric vectors), and {@link JSONObject} entries are visited value-by-value.
     */
    protected void collectFromResult(List<AIMetadata.Label> labels, String action, Object raw) {
        if (raw instanceof String value) {
            if (StringUtils.isNotBlank(value)) {
                labels.add(new AIMetadata.Label(action + "/" + value.trim(), 1.0F));
            }
            return;
        }
        if (raw instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                Object item = array.get(i);
                if (item instanceof String || item instanceof JSONArray || item instanceof JSONObject) {
                    collectFromResult(labels, action, item);
                }
            }
            return;
        }
        if (raw instanceof JSONObject object) {
            for (String childKey : object.keySet()) {
                collectFromResult(labels, action, object.get(childKey));
            }
        }
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }

}
