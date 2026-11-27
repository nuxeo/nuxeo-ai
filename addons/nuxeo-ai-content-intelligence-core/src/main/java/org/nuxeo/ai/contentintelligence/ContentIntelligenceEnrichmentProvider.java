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
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.nuxeo.ai.contentintelligence.ContentIntelligenceConstants.DESCRIPTION_ACTION_KEYS;
import static org.nuxeo.ai.contentintelligence.ContentIntelligenceJson.optArray;
import static org.nuxeo.ai.contentintelligence.ContentIntelligenceJson.optBoolean;
import static org.nuxeo.ai.contentintelligence.ContentIntelligenceJson.optObject;
import static org.nuxeo.ai.contentintelligence.ContentIntelligenceJson.optString;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.getBlobFromProvider;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyland.sdk.cic.http.client.CICError;
import org.hyland.sdk.cic.http.client.CICServiceException;
import org.hyland.sdk.cic.ke.object.EnrichmentResult;
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
import org.nuxeo.runtime.api.Framework;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Enrichment provider that delegates to {@link ContentIntelligenceService} to call the Hyland Content Intelligence
 * Knowledge Enrichment API and maps its result into {@link EnrichmentMetadata}.
 */
public class ContentIntelligenceEnrichmentProvider extends AbstractEnrichmentProvider implements EnrichmentCachable {

    // Options that shape the Knowledge Enrichment response must also be part of the fingerprint in getCacheKey.
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

    protected static final String STATUS_PROCESSING = "PROCESSING";

    protected static final String JSON_KEY_ID = "id";

    protected static final String JSON_KEY_ERROR = "error";

    protected static final String JSON_KEY_ERROR_TYPE = "errorType";

    protected static final String JSON_KEY_ERROR_MESSAGE = "message";

    /** Max number of action-level errors quoted in a non-terminal-status WARN before truncation. */
    protected static final int MAX_ERRORS_IN_SUMMARY = 5;

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

    protected ContentIntelligenceRequest request;

    protected String similarMetadata;

    protected String extraJsonPayload;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        configName = StringUtils.defaultIfBlank(descriptor.options.get(OPTION_CONFIG_NAME), DEFAULT_CONFIG_NAME).trim();
        similarMetadata = StringUtils.trimToNull(descriptor.options.get(OPTION_SIMILAR_METADATA));
        extraJsonPayload = StringUtils.trimToNull(descriptor.options.get(OPTION_EXTRA_JSON_PAYLOAD));
        try {
            request = ContentIntelligenceRequest.of(descriptor.options.getOrDefault(OPTION_ACTIONS, DEFAULT_ACTIONS),
                    similarMetadata, extraJsonPayload);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid Knowledge Enrichment configuration of %s: %s", descriptor.name,
                            e.getMessage()),
                    e);
        }
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

    /** Resolves the {@link ContentIntelligenceService}. Overridable for testing. */
    protected ContentIntelligenceService getService() {
        ContentIntelligenceService service = Framework.getService(ContentIntelligenceService.class);
        if (service == null) {
            throw new NuxeoException("ContentIntelligenceService is not available");
        }
        return service;
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {
        if (blobTextFromDoc.getBlobs().isEmpty()) {
            return emptyList();
        }
        ContentIntelligenceService service = getService();

        List<EnrichmentMetadata> enriched = new ArrayList<>();
        for (Map.Entry<String, ManagedBlob> entry : blobTextFromDoc.getBlobs().entrySet()) {
            String xPath = entry.getKey();
            Blob blob = resolveBlob(entry.getValue());
            if (blob == null) {
                log.warn("Could not resolve blob for property {}", xPath);
                continue;
            }

            Blob payload = transcodeIfNeeded(blob);

            EnrichmentResult result;
            try {
                result = service.enrich(configName, payload, request);
            } catch (NuxeoException e) {
                handleFailedCall(blobTextFromDoc, xPath, e);
                continue;
            }
            if (result == null) {
                handleFailedCall(blobTextFromDoc, xPath, new NuxeoException("No Knowledge Enrichment result"));
                continue;
            }

            ObjectNode response = ContentIntelligenceResultSerializer.toJson(result);
            enriched.addAll(processResponse(blobTextFromDoc, xPath, response));
        }
        return enriched;
    }

    /**
     * Handles a failed Hyland CI call. HTTP 5xx and 429 answers and failures without an HTTP answer (network error,
     * unreadable answer, configuration error, ...) are rethrown so the upstream stream processor accounts for them;
     * other HTTP 4xx answers (definitive client errors) are logged and the blob is dropped.
     */
    protected void handleFailedCall(BlobTextFromDocument blobTextFromDoc, String xPath, NuxeoException e) {
        CICServiceException serviceException = ExceptionUtils.throwableOfType(e, CICServiceException.class);
        int code = serviceException == null ? -1 : serviceException.statusCode();
        if (serviceException == null || isRetryable(code)) {
            throw new NuxeoException(String.format("Knowledge Enrichment call failed (status=%d) for %s/%s: %s", code,
                    blobTextFromDoc.getId(), xPath, e.getMessage()), e);
        }
        String detail = serviceException.remoteCause().map(CICError::message).map(m -> " (" + m + ")").orElse("");
        log.warn("Knowledge Enrichment call unsuccessful (status={}) for {}/{}: {}{}", code, blobTextFromDoc.getId(),
                xPath, e.getMessage(), detail);
    }

    /** Failures without HTTP answer (-1) and 5xx / 429 answers are considered retryable. */
    protected boolean isRetryable(int code) {
        return code < 0 || code == 429 || (code >= 500 && code < 600);
    }

    /**
     * Turns each {@code results[]} entry of the Knowledge Enrichment response (as written by
     * {@link ContentIntelligenceResultSerializer}) into an {@link EnrichmentMetadata} bundle. An entry that carries
     * only a successful description block (no taggable suggestions) still yields an {@link EnrichmentMetadata} with
     * empty labels so that {@link ContentIntelligenceDescriptionListener} has an event to consume and can write
     * {@code dc:description}. The raw blob stores the response under a {@code response} key.
     */
    protected Collection<EnrichmentMetadata> processResponse(BlobTextFromDocument blobTextFromDoc, String xPath,
            ObjectNode response) {
        ObjectNode wrapper = JsonNodeFactory.instance.objectNode();
        wrapper.set(JSON_KEY_RESPONSE, response);
        String rawJson = wrapper.toString();

        String status = optString(response, JSON_KEY_STATUS, "");
        if (!status.isEmpty() && !STATUS_SUCCESS.equalsIgnoreCase(status)
                && !STATUS_PARTIAL_SUCCESS.equalsIgnoreCase(status)
                && !STATUS_PARTIAL_FAILURE.equalsIgnoreCase(status)) {
            logNonTerminalStatus(blobTextFromDoc, xPath, status, response, rawJson);
            return emptyList();
        }

        ArrayNode results = optArray(response, JSON_KEY_RESULTS);
        if (results == null || results.isEmpty()) {
            return emptyList();
        }

        String rawKey = null;
        List<EnrichmentMetadata> metadata = new ArrayList<>();
        for (JsonNode item : results) {
            if (!(item instanceof ObjectNode entry)) {
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
     * Emits a single, structured WARN line for a non-terminal Hyland CI status (FAILURE, PROCESSING, ...) and
     * relegates the full response body to DEBUG so production logs do not get flooded with multi-KB JSON dumps for
     * every server-side hiccup. The summary carries the bits an operator actually needs to triage the failure: the
     * document + xPath being enriched, the CIC job ID, and the per-action {@code errorType: message} pairs (capped
     * at {@link #MAX_ERRORS_IN_SUMMARY} so a wide actions list cannot blow the line length out).
     * <p>
     * {@code PROCESSING} gets a tailored message pointing at the polling-budget knobs because the remediation is
     * different (raise the budget) from terminal {@code FAILURE} (typically a transient CIC server-side issue).
     */
    protected void logNonTerminalStatus(BlobTextFromDocument blobTextFromDoc, String xPath, String status,
            ObjectNode response, String rawJson) {
        String jobId = optString(response, JSON_KEY_ID, "?");
        String errorSummary = summarizeActionErrors(response);
        String docId = blobTextFromDoc.getId();
        if (STATUS_PROCESSING.equalsIgnoreCase(status)) {
            log.warn("Knowledge Enrichment still PROCESSING after polling budget exhausted for {}/{} (jobId={}). "
                    + "The result may arrive later but will be discarded; consider raising "
                    + "nuxeo.hyland.cic.pullResultsMaxTries / pullResultsSleepInterval if this recurs.", docId, xPath,
                    jobId);
        } else if (errorSummary.isEmpty()) {
            log.warn("Knowledge Enrichment returned status '{}' for {}/{} (jobId={}), skipping.", status, docId, xPath,
                    jobId);
        } else {
            log.warn("Knowledge Enrichment returned status '{}' for {}/{} (jobId={}), skipping. Action errors: {}",
                    status, docId, xPath, jobId, errorSummary);
        }
        log.debug("Full Knowledge Enrichment response body for {}/{}: {}", docId, xPath, rawJson);
    }

    /**
     * Flattens the {@code response.results[*]} action blocks into a compact {@code action[errorType: message], ...}
     * string covering only the actions that actually failed ({@code isSuccess=false} or carry an {@code error} block).
     * Returns an empty string when the response carries no per-action errors (e.g. a {@code PROCESSING} payload).
     */
    protected String summarizeActionErrors(ObjectNode response) {
        ArrayNode results = optArray(response, JSON_KEY_RESULTS);
        if (results == null || results.isEmpty()) {
            return "";
        }
        List<String> errors = new ArrayList<>();
        outer:
        for (JsonNode item : results) {
            if (!(item instanceof ObjectNode entry)) {
                continue;
            }
            for (Map.Entry<String, JsonNode> field : entry.properties()) {
                String key = field.getKey();
                if (JSON_KEY_OBJECT_KEY.equals(key)) {
                    continue;
                }
                ObjectNode action = optObject(entry, key);
                if (action == null) {
                    continue;
                }
                if (optBoolean(action, JSON_KEY_IS_SUCCESS, true)) {
                    continue;
                }
                ObjectNode err = optObject(action, JSON_KEY_ERROR);
                String errorType = err == null ? "?" : optString(err, JSON_KEY_ERROR_TYPE, "?");
                String message = err == null ? "" : optString(err, JSON_KEY_ERROR_MESSAGE, "");
                String formatted = message.isEmpty() ? String.format("%s[%s]", key, errorType)
                        : String.format("%s[%s: %s]", key, errorType, message);
                errors.add(formatted);
                if (errors.size() >= MAX_ERRORS_IN_SUMMARY) {
                    errors.add("...");
                    break outer;
                }
            }
        }
        return String.join(", ", errors);
    }

    /**
     * @return {@code true} when the entry carries at least one successful description action, signalling that the
     *         description listener has a value to extract from the raw blob.
     */
    protected boolean hasDescription(ObjectNode entry) {
        for (String actionKey : DESCRIPTION_ACTION_KEYS) {
            ObjectNode action = optObject(entry, actionKey);
            if (action != null && optBoolean(action, JSON_KEY_IS_SUCCESS, false)) {
                JsonNode value = action.get(JSON_KEY_RESULT);
                if (value != null && value.isTextual() && StringUtils.isNotBlank(value.textValue())) {
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
    protected List<LabelSuggestion> toLabelSuggestions(ObjectNode entry) {
        Map<String, List<AIMetadata.Label>> labelsByAction = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> field : entry.properties()) {
            String key = field.getKey();
            if (JSON_KEY_OBJECT_KEY.equals(key) || DESCRIPTION_ACTION_KEYS.contains(key)) {
                continue;
            }
            ObjectNode action = optObject(entry, key);
            if (action == null || !optBoolean(action, JSON_KEY_IS_SUCCESS, false) || !action.has(JSON_KEY_RESULT)) {
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
     * array entries are visited one by one (any non-string member is ignored to avoid picking up numeric vectors),
     * and object entries are visited value-by-value.
     */
    protected void collectFromResult(List<AIMetadata.Label> labels, JsonNode raw) {
        if (raw == null) {
            return;
        }
        if (raw.isTextual()) {
            String value = raw.textValue();
            if (StringUtils.isNotBlank(value)) {
                labels.add(new AIMetadata.Label(value.trim(), 1.0F));
            }
            return;
        }
        if (raw.isArray() || raw.isObject()) {
            for (JsonNode item : raw) {
                collectFromResult(labels, item);
            }
        }
    }

    /**
     * Keys the cache on the blob digests and on a fingerprint of the options that shape the Knowledge Enrichment
     * response, so that changing them (e.g. {@code maxWordCount} in {@code extraJsonPayload}) calls the service again
     * instead of returning the result cached for the previous configuration.
     */
    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        String key = makeKeyUsingBlobDigests(blobTextFromDoc, name);
        if (key == null) {
            return null;
        }
        String config = String.join("\n", "configName=" + configName,
                "actions=" + String.join(",", request.actionNames()),
                "similarMetadata=" + StringUtils.defaultString(similarMetadata),
                "extraJsonPayload=" + StringUtils.defaultString(extraJsonPayload));
        return key + "_" + sha256Hex(config);
    }

}
