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

import java.util.List;

import org.hyland.sdk.cic.ke.object.ActionResult;
import org.hyland.sdk.cic.ke.object.ClassificationResult;
import org.hyland.sdk.cic.ke.object.EnrichmentData;
import org.hyland.sdk.cic.ke.object.EnrichmentResult;
import org.hyland.sdk.cic.ke.object.EnrichmentResultEntry;
import org.hyland.sdk.cic.ke.object.ImageClassificationResult;
import org.hyland.sdk.cic.ke.object.ImageDescription;
import org.hyland.sdk.cic.ke.object.ImageEmbedding;
import org.hyland.sdk.cic.ke.object.ImageMetadata;
import org.hyland.sdk.cic.ke.object.NamedEntities;
import org.hyland.sdk.cic.ke.object.ProcessingError;
import org.hyland.sdk.cic.ke.object.TextClassificationResult;
import org.hyland.sdk.cic.ke.object.TextEmbedding;
import org.hyland.sdk.cic.ke.object.TextMetadata;
import org.hyland.sdk.cic.ke.object.TextSummary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Writes a Knowledge Enrichment {@link EnrichmentResult} as the JSON shape of the API response ({@code id},
 * {@code status}, {@code results[]} with one {@code {isSuccess, result | error}} block per action response key). The
 * label extraction of {@link ContentIntelligenceEnrichmentProvider} and the description extraction of
 * {@link ContentIntelligenceDescriptionListener} read that shape, the latter from the stored raw blob.
 *
 * @since 5.0.5
 */
public final class ContentIntelligenceResultSerializer {

    // not JacksonUtil.MAPPER: its NON_EMPTY inclusion would drop empty values
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ContentIntelligenceResultSerializer() {
        // utility class
    }

    public static ObjectNode toJson(EnrichmentResult result) {
        ObjectNode json = MAPPER.createObjectNode();
        json.put("id", result.id());
        if (result.timestamp() != null) {
            json.put("timestamp", result.timestamp());
        }
        if (result.status() != null) {
            json.put("status", result.status());
        }
        ArrayNode results = json.putArray("results");
        for (EnrichmentResultEntry entry : result.results()) {
            results.add(toJson(entry));
        }
        return json;
    }

    protected static ObjectNode toJson(EnrichmentResultEntry entry) {
        ObjectNode json = MAPPER.createObjectNode();
        json.put("objectKey", entry.objectKey());
        putAction(json, "imageDescription", entry.imageDescription());
        putAction(json, "imageMetadata", entry.imageMetadata());
        putAction(json, "textMetadata", entry.textMetadata());
        putAction(json, "textSummary", entry.textSummary());
        putAction(json, "textClassification", entry.textClassification());
        putAction(json, "imageClassification", entry.imageClassification());
        putAction(json, "textEmbeddings", entry.textEmbeddings());
        putAction(json, "imageEmbeddings", entry.imageEmbeddings());
        putAction(json, "namedEntityText", entry.namedEntityText());
        putAction(json, "namedEntityImage", entry.namedEntityImage());
        putAction(json, "pretrainedClassification", entry.pretrainedClassification());
        List<ProcessingError> errors = entry.generalProcessingErrors();
        if (!errors.isEmpty()) {
            ArrayNode array = json.putArray("generalProcessingErrors");
            errors.forEach(error -> array.add(toJson(error)));
        }
        return json;
    }

    protected static void putAction(ObjectNode entry, String key, ActionResult<?> action) {
        if (action == null) {
            return;
        }
        ObjectNode json = entry.putObject(key);
        json.put("isSuccess", action.isSuccess());
        if (action.isSuccess()) {
            if (action.result() != null) {
                json.set("result", payload(action.result()));
            }
        } else if (action.error() != null) {
            json.set("error", toJson(action.error()));
        }
    }

    protected static ObjectNode toJson(ProcessingError error) {
        ObjectNode json = MAPPER.createObjectNode();
        if (error.type() != null && error.type().value() != null) {
            json.put("errorType", error.type().value());
        }
        if (error.message() != null) {
            json.put("message", error.message());
        }
        return json;
    }

    protected static JsonNode payload(EnrichmentData data) {
        return switch (data) {
        case ImageDescription d -> MAPPER.getNodeFactory().textNode(d.value());
        case TextSummary d -> MAPPER.getNodeFactory().textNode(d.value());
        case TextClassificationResult d -> MAPPER.getNodeFactory().textNode(d.value());
        case ImageClassificationResult d -> MAPPER.getNodeFactory().textNode(d.value());
        case NamedEntities d -> MAPPER.valueToTree(d.entities());
        case ImageMetadata d -> MAPPER.valueToTree(d.properties());
        case TextMetadata d -> MAPPER.valueToTree(d.properties());
        case ImageEmbedding d -> MAPPER.valueToTree(d.vector());
        case TextEmbedding d -> MAPPER.valueToTree(d.vectors());
        case ClassificationResult d -> {
            ObjectNode json = MAPPER.createObjectNode();
            json.put("classification", d.classification());
            json.put("confidence", d.confidence());
            yield json;
        }
        default -> MAPPER.valueToTree(data);
        };
    }
}
