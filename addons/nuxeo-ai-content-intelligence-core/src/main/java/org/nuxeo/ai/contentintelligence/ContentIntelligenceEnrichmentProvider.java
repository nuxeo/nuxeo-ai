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
import static org.nuxeo.ai.contentintelligence.ContentIntelligenceConstants.DESCRIPTION_ACTION_KEYS;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.getBlobFromProvider;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.nuxeo.ecm.core.api.impl.blob.ByteArrayBlob;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.hyland.content.intelligence.service.enrichment.HylandKEService;
import org.nuxeo.runtime.api.Framework;

/**
 * Enrichment provider that delegates to {@link HylandKEService} to call the Hyland Content Intelligence
 * Knowledge Enrichment API and maps the wrapped JSON response into {@link EnrichmentMetadata}.
 */
public class ContentIntelligenceEnrichmentProvider extends AbstractEnrichmentProvider implements EnrichmentCachable {

    public static final String OPTION_CONFIG_NAME = "configName";

    public static final String OPTION_ACTIONS = "actions";

    public static final String OPTION_SIMILAR_METADATA = "similarMetadata";

    public static final String OPTION_EXTRA_JSON_PAYLOAD = "extraJsonPayload";

    public static final String DEFAULT_CONFIG_NAME = "default";

    /**
     * Default action set when callers omit the {@code actions} option: only the long-form description is requested.
     */
    public static final String DEFAULT_ACTIONS = "image-description";

    protected static final String JSON_KEY_OBJECT_KEY = "objectKey";

    protected static final String JSON_KEY_IS_SUCCESS = "isSuccess";

    protected static final String JSON_KEY_RESULT = "result";

    protected static final String JSON_KEY_RESPONSE = "response";

    protected static final String JSON_KEY_RESULTS = "results";

    protected static final String JSON_KEY_STATUS = "status";

    protected static final String STATUS_SUCCESS = "SUCCESS";

    protected static final String STATUS_PARTIAL_SUCCESS = "PARTIAL_SUCCESS";

    protected static final String STATUS_PARTIAL_FAILURE = "PARTIAL_FAILURE";

    /**
     * Image MIME types Hyland CI's {@code image-*} actions accept natively (per the connector documentation). Any
     * other {@code image/*} blob is transparently transcoded to JPEG by {@link #transcodeIfNeeded(Blob)} before being
     * sent to the API, so the deployment can route formats like BMP, GIF, WebP, ... through the enrichment pipeline
     * end-to-end. Compared case-insensitively.
     */
    protected static final Set<String> CIC_NATIVE_IMAGE_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/tiff");

    /** MIME type and ImageIO format used when an unsupported image format must be transcoded. */
    protected static final String TRANSCODE_TARGET_MIME_TYPE = "image/jpeg";

    protected static final String TRANSCODE_TARGET_FORMAT = "jpg";

    private static final Logger log = LogManager.getLogger(ContentIntelligenceEnrichmentProvider.class);

    protected String configName;

    protected List<String> actions;

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
        similarMetadata = StringUtils.trimToNull(descriptor.options.get(OPTION_SIMILAR_METADATA));
        extraJsonPayload = StringUtils.trimToNull(descriptor.options.get(OPTION_EXTRA_JSON_PAYLOAD));
    }

    protected List<String> splitCsv(String value) {
        if (StringUtils.isBlank(value)) {
            return new ArrayList<>();
        }
        return Arrays.stream(value.split(","))
                     .map(String::trim)
                     .filter(StringUtils::isNotBlank)
                     .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /**
     * Hyland Content Intelligence performs the actual upload itself (presigned PUT -> POST /context/process)
     * and is the authoritative source for what size it can ingest. The Nuxeo AI framework's default 5 MB cap
     * inherited from {@link EnrichmentDescriptor#DEFAULT_MAX_SIZE} would silently drop any larger blob in
     * {@code EnrichingStreamProcessor} before the provider is ever invoked, which is wrong for this connector:
     * we want every eligible blob to reach the CIC API and let CIC enforce its own ceilings. Returning
     * {@code true} unconditionally disables that pre-check while leaving MIME-type filtering intact.
     */
    @Override
    public boolean supportsSize(long size) {
        return true;
    }

    /** Resolves the backing storage blob for a {@link ManagedBlob}. Overridable for testing. */
    protected Blob resolveBlob(ManagedBlob managedBlob) {
        return getBlobFromProvider(managedBlob);
    }

    /**
     * Hyland CI's {@code image-*} actions only accept JPEG, PNG and TIFF natively. For any other {@code image/*}
     * blob (BMP, GIF, WebP, ...) we transcode the bytes to JPEG via {@link ImageIO} so the blob can still be
     * enriched end-to-end. Non-image blobs and natively-supported image blobs are returned unchanged. If transcoding
     * fails for any reason (unreadable bytes, missing ImageIO plugin, I/O error) we log a warning and return the
     * original blob so the upstream CIC API can either accept it or surface the error.
     */
    protected Blob transcodeIfNeeded(Blob blob) {
        if (blob == null) {
            return null;
        }
        String mimeType = blob.getMimeType();
        if (StringUtils.isBlank(mimeType)) {
            return blob;
        }
        String normalized = mimeType.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("image/") || CIC_NATIVE_IMAGE_MIME_TYPES.contains(normalized)) {
            return blob;
        }
        try (InputStream in = blob.getStream()) {
            BufferedImage source = ImageIO.read(in);
            if (source == null) {
                log.warn("ImageIO has no reader for blob {} (mime={}); sending the original bytes to Hyland CI",
                        blob.getFilename(), mimeType);
                return blob;
            }
            // BMP/GIF/PNG may carry an alpha channel or be indexed - JPEG cannot encode either of those, so we
            // composite onto a solid background to keep the colors faithful and avoid ImageIO returning a 0-byte
            // result.
            BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            try {
                g.drawImage(source, 0, 0, null);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(rgb, TRANSCODE_TARGET_FORMAT, out)) {
                log.warn("ImageIO could not write blob {} as {}; sending the original bytes to Hyland CI",
                        blob.getFilename(), TRANSCODE_TARGET_FORMAT);
                return blob;
            }
            // ByteArrayBlob keeps the JPEG bytes in memory: avoids spilling another temp file on every transcode
            // and works in unit tests where no Nuxeo Framework is initialised to allocate them.
            Blob transcoded = new ByteArrayBlob(out.toByteArray(), TRANSCODE_TARGET_MIME_TYPE);
            transcoded.setFilename(replaceExtension(blob.getFilename(), TRANSCODE_TARGET_FORMAT));
            log.debug("Transcoded blob {} from {} to {} ({} bytes) for Hyland CI image enrichment",
                    blob.getFilename(), mimeType, TRANSCODE_TARGET_MIME_TYPE, transcoded.getLength());
            return transcoded;
        } catch (IOException e) {
            log.warn("Failed to transcode blob {} (mime={}) to JPEG, sending original bytes: {}", blob.getFilename(),
                    mimeType, e.getMessage());
            return blob;
        }
    }

    /** Replaces (or appends) the filename extension so the transcoded blob carries a coherent {@code .jpg} suffix. */
    protected String replaceExtension(String filename, String extension) {
        if (StringUtils.isBlank(filename)) {
            return "image." + extension;
        }
        int idx = filename.lastIndexOf('.');
        if (idx > 0) {
            return filename.substring(0, idx) + "." + extension;
        }
        return filename + "." + extension;
    }

    /** Resolves the {@link HylandKEService}. Overridable for testing. */
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
        for (Map.Entry<String, ManagedBlob> entry : blobTextFromDoc.getBlobs().entrySet()) {
            String xPath = entry.getKey();
            Blob blob = resolveBlob(entry.getValue());
            if (blob == null) {
                log.warn("Could not resolve blob for property {}", xPath);
                continue;
            }

            Blob payload = transcodeIfNeeded(blob);

            ServiceCallResult result;
            try {
                result = service.enrich(configName, payload, actions, Collections.emptyList(), similarMetadata,
                        extraJsonPayload);
            } catch (IOException e) {
                throw new NuxeoException(
                        "Knowledge Enrichment call failed for " + blobTextFromDoc.getId() + "/" + xPath, e);
            }

            if (result == null || result.callFailed()) {
                handleFailedCall(blobTextFromDoc, xPath, result);
                continue;
            }

            enriched.addAll(processResponse(blobTextFromDoc, xPath, result));
        }
        return enriched;
    }

    /**
     * Handles an unsuccessful HTTP response from Hyland CI. 5xx and 429 responses are rethrown so the upstream
     * stream-processor retry policy can take over; 4xx responses (definitive client errors) are logged and dropped.
     */
    protected void handleFailedCall(BlobTextFromDocument blobTextFromDoc, String xPath, ServiceCallResult result) {
        int code = result == null ? -1 : result.getResponseCode();
        String msg = result == null ? "null result" : result.getResponseMessage();
        String body = result == null ? "" : StringUtils.defaultString(result.getResponse());

        if (isRetryable(code)) {
            log.debug("Retryable Knowledge Enrichment failure response body for {}/{}: {}", blobTextFromDoc.getId(),
                    xPath, body);
            throw new NuxeoException(String.format(
                    "Knowledge Enrichment call failed (status=%d, message=%s) for %s/%s", code, msg,
                    blobTextFromDoc.getId(), xPath));
        }
        log.warn("Knowledge Enrichment call unsuccessful (status={}, message={}) for {}/{}", code, msg,
                blobTextFromDoc.getId(), xPath);
        log.debug("Unsuccessful Knowledge Enrichment response body for {}/{}: {}", blobTextFromDoc.getId(), xPath,
                body);
    }

    /** Network-level (-1) and 5xx / 429 responses are considered retryable. */
    protected boolean isRetryable(int code) {
        return code < 0 || code == 429 || (code >= 500 && code < 600);
    }

    /**
     * Parses the wrapped Knowledge Enrichment response and turns each {@code results[]} entry into an
     * {@link EnrichmentMetadata} bundle. An entry that carries only a successful description block (no taggable
     * suggestions) still yields an {@link EnrichmentMetadata} with empty labels so that
     * {@link ContentIntelligenceDescriptionListener} has an event to consume and can write {@code dc:description}.
     */
    protected Collection<EnrichmentMetadata> processResponse(BlobTextFromDocument blobTextFromDoc, String xPath,
            ServiceCallResult result) {
        JSONObject response;
        String rawJson;
        try {
            response = result.getResponseAsJSONObject();
            rawJson = result.toJsonString();
        } catch (JSONException | NuxeoException e) {
            // Hyland CI's helper wraps non-object payloads in NuxeoException, malformed JSON surfaces as
            // JSONException; both mean we cannot safely interpret the response.
            log.warn("Knowledge Enrichment response is not a JSON object: {}", e.getMessage());
            return emptyList();
        }

        String status = response.optString(JSON_KEY_STATUS, "");
        if (!status.isEmpty() && !STATUS_SUCCESS.equalsIgnoreCase(status)
                && !STATUS_PARTIAL_SUCCESS.equalsIgnoreCase(status)
                && !STATUS_PARTIAL_FAILURE.equalsIgnoreCase(status)) {
            log.warn("Knowledge Enrichment returned status '{}', skipping. Response body: {}", status, rawJson);
            return emptyList();
        }

        JSONArray results = response.optJSONArray(JSON_KEY_RESULTS);
        if (results == null || results.length() == 0) {
            return emptyList();
        }

        String rawKey = null;
        List<EnrichmentMetadata> metadata = new ArrayList<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject entry = results.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            List<LabelSuggestion> suggestions = toLabelSuggestions(entry);
            if (suggestions.isEmpty() && !hasDescription(entry)) {
                continue;
            }
            if (rawKey == null) {
                rawKey = saveJsonAsRawBlob(rawJson);
            }
            metadata.add(new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc).withLabels(suggestions)
                                                                                    .withRawKey(rawKey)
                                                                                    .withDocumentProperties(
                                                                                            Collections.singleton(
                                                                                                    xPath))
                                                                                    .build());
        }
        return metadata;
    }

    /**
     * @return {@code true} when the entry carries at least one successful description action, signalling that the
     *         description listener has a value to extract from the raw blob.
     */
    protected boolean hasDescription(JSONObject entry) {
        for (String actionKey : DESCRIPTION_ACTION_KEYS) {
            JSONObject action = entry.optJSONObject(actionKey);
            if (action != null && action.optBoolean(JSON_KEY_IS_SUCCESS, false)) {
                Object value = action.opt(JSON_KEY_RESULT);
                if (value instanceof String s && StringUtils.isNotBlank(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Converts each successful action result block ({@code namedEntityImage}, {@code namedEntityText}, ...) into a
     * {@link LabelSuggestion} whose {@link LabelSuggestion#getProperty()
     * property} is the Hyland action key and whose values are the plain extracted strings (no {@code action/} prefix).
     * Action keys listed in {@link ContentIntelligenceConstants#DESCRIPTION_ACTION_KEYS} are deliberately skipped.
     */
    protected List<LabelSuggestion> toLabelSuggestions(JSONObject entry) {
        Map<String, List<AIMetadata.Label>> labelsByAction = new LinkedHashMap<>();
        for (String key : entry.keySet()) {
            if (JSON_KEY_OBJECT_KEY.equals(key) || DESCRIPTION_ACTION_KEYS.contains(key)) {
                continue;
            }
            JSONObject action = entry.optJSONObject(key);
            if (action == null || !action.optBoolean(JSON_KEY_IS_SUCCESS, false) || !action.has(JSON_KEY_RESULT)) {
                continue;
            }
            List<AIMetadata.Label> labels = labelsByAction.computeIfAbsent(key, k -> new ArrayList<>());
            collectFromResult(labels, action.get(JSON_KEY_RESULT));
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
     * numeric vectors), and {@link JSONObject} entries are visited value-by-value.
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
