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

import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Lenient accessors over Jackson trees for reading Hyland Content Intelligence payloads. They keep the semantics the
 * previous {@code org.json} {@code opt*} lookups had: a missing or mistyped member yields the default instead of
 * failing.
 *
 * @since 5.0.5
 */
final class ContentIntelligenceJson {

    /**
     * Accepts the lenient syntax {@code org.json} used to accept (and the connector still accepts upstream), so a
     * payload the connector parsed is not dropped here.
     */
    private static final ObjectReader READER = MAPPER.reader()
                                                     .withFeatures(JsonReadFeature.ALLOW_TRAILING_COMMA,
                                                             JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS,
                                                             JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS,
                                                             JsonReadFeature.ALLOW_SINGLE_QUOTES,
                                                             JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES);

    private ContentIntelligenceJson() {
        // utility class
    }

    /**
     * @return the parsed JSON value ({@code MissingNode} for an empty payload)
     * @throws JsonProcessingException if the payload is not valid JSON
     */
    static JsonNode readTree(String json) throws JsonProcessingException {
        return READER.readTree(json);
    }

    /**
     * @return the parsed JSON object, or {@code null} when the payload is empty or not a JSON object
     * @throws JsonProcessingException if the payload is not valid JSON
     */
    static ObjectNode readObject(String json) throws JsonProcessingException {
        return json == null ? null : READER.readTree(json) instanceof ObjectNode object ? object : null;
    }

    static ObjectNode optObject(JsonNode node, String key) {
        return node.get(key) instanceof ObjectNode object ? object : null;
    }

    static ArrayNode optArray(JsonNode node, String key) {
        return node.get(key) instanceof ArrayNode array ? array : null;
    }

    /** Value nodes are returned as text, containers as their JSON form, {@code null} or missing as the default. */
    static String optString(JsonNode node, String key, String defaultValue) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        return value.isValueNode() ? value.asText() : value.toString();
    }

    /** Accepts JSON booleans and the strings {@code "true"} / {@code "false"} (any case), otherwise the default. */
    static boolean optBoolean(JsonNode node, String key, boolean defaultValue) {
        JsonNode value = node.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isTextual()) {
            String text = value.textValue();
            if ("true".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text)) {
                return false;
            }
        }
        return defaultValue;
    }
}
