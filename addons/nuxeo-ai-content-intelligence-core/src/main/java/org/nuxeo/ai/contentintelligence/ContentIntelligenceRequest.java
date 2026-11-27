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

import static org.nuxeo.ai.contentintelligence.ContentIntelligenceJson.readObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyland.sdk.cic.ke.object.Action;
import org.hyland.sdk.cic.ke.object.ActionConfig;
import org.hyland.sdk.cic.ke.object.ProcessRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Knowledge Enrichment request of an enrichment provider, parsed once from its options so that a wrong
 * configuration fails when the provider is initialized, not on the first document.
 * <ul>
 * <li>actions accept the API names ({@code imageDescription}) and the kebab-case form used in the Nuxeo AI
 * configuration ({@code image-description});</li>
 * <li>{@code similarMetadata} is a JSON array of string maps, sent as {@code kSimilarMetadata} on every action;</li>
 * <li>{@code extraJsonPayload} supports {@code maxWordCount}, {@code model}, {@code category} and
 * {@code instructions} (applied to every action) and {@code saveResultInContentLakeRepository}; other keys are
 * ignored with a warning.</li>
 * </ul>
 *
 * @since 5.0.5
 */
public record ContentIntelligenceRequest(List<Action> actions, ActionConfig actionConfig,
        boolean saveResultInContentLakeRepository) {

    private static final Logger log = LogManager.getLogger(ContentIntelligenceRequest.class);

    protected static final String KEY_MAX_WORD_COUNT = "maxWordCount";

    protected static final String KEY_MODEL = "model";

    protected static final String KEY_CATEGORY = "category";

    protected static final String KEY_INSTRUCTIONS = "instructions";

    protected static final String KEY_SAVE_RESULT = "saveResultInContentLakeRepository";

    protected static final Set<String> SUPPORTED_EXTRA_KEYS = Set.of(KEY_MAX_WORD_COUNT, KEY_MODEL, KEY_CATEGORY,
            KEY_INSTRUCTIONS, KEY_SAVE_RESULT);

    /** Action names, in both API (camelCase) and Nuxeo AI configuration (kebab-case) spelling. */
    protected static final Map<String, Action> ACTIONS_BY_NAME = actionsByName();

    public ContentIntelligenceRequest {
        actions = List.copyOf(actions);
    }

    private static Map<String, Action> actionsByName() {
        Map<String, Action> map = new HashMap<>();
        for (Action action : Action.values()) {
            map.put(action.value(), action);
            map.put(action.name().toLowerCase(Locale.ROOT).replace('_', '-'), action);
        }
        return Map.copyOf(map);
    }

    /**
     * @throws IllegalArgumentException if no action, an unknown action or a malformed JSON option is given
     */
    public static ContentIntelligenceRequest of(String actionsCsv, String similarMetadataJson,
            String extraJsonPayload) {
        ActionConfig.Builder config = ActionConfig.builder();
        if (StringUtils.isNotBlank(similarMetadataJson)) {
            config.kSimilarMetadata(parseSimilarMetadata(similarMetadataJson));
        }
        boolean saveResult = false;
        if (StringUtils.isNotBlank(extraJsonPayload)) {
            saveResult = applyExtraPayload(parseObject(extraJsonPayload, "extraJsonPayload"), config);
        }
        return new ContentIntelligenceRequest(parseActions(actionsCsv), config.build(), saveResult);
    }

    protected static List<Action> parseActions(String actionsCsv) {
        Set<Action> actions = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String name : StringUtils.defaultString(actionsCsv).split(",")) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Action action = ACTIONS_BY_NAME.get(trimmed);
            if (action == null) {
                unknown.add(trimmed);
            } else {
                actions.add(action);
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(String.format("Unknown Knowledge Enrichment action(s) %s, expected %s",
                    unknown, Arrays.stream(Action.values()).map(Action::value).collect(Collectors.toList())));
        }
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("At least one Knowledge Enrichment action is required");
        }
        return new ArrayList<>(actions);
    }

    protected static List<Map<String, String>> parseSimilarMetadata(String json) {
        JsonNode node;
        try {
            node = ContentIntelligenceJson.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("similarMetadata is not valid JSON: " + e.getOriginalMessage(), e);
        }
        if (!(node instanceof ArrayNode array)) {
            throw new IllegalArgumentException("similarMetadata must be a JSON array of objects");
        }
        List<Map<String, String>> metadata = new ArrayList<>();
        for (JsonNode item : array) {
            metadata.add(toStringMap(item, "similarMetadata"));
        }
        return metadata;
    }

    protected static ObjectNode parseObject(String json, String option) {
        ObjectNode object;
        try {
            object = readObject(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(option + " is not valid JSON: " + e.getOriginalMessage(), e);
        }
        if (object == null) {
            throw new IllegalArgumentException(option + " must be a JSON object");
        }
        return object;
    }

    /** @return the {@code saveResultInContentLakeRepository} flag */
    protected static boolean applyExtraPayload(ObjectNode payload, ActionConfig.Builder config) {
        JsonNode maxWordCount = payload.get(KEY_MAX_WORD_COUNT);
        if (maxWordCount != null && !maxWordCount.isNull()) {
            config.maxWordCount(toInt(maxWordCount, "extraJsonPayload." + KEY_MAX_WORD_COUNT));
        }
        String model = textOrNull(payload, KEY_MODEL);
        if (model != null) {
            config.model(model);
        }
        String category = textOrNull(payload, KEY_CATEGORY);
        if (category != null) {
            config.category(category);
        }
        JsonNode instructions = payload.get(KEY_INSTRUCTIONS);
        if (instructions != null && !instructions.isNull()) {
            config.instructions(toStringMap(instructions, "extraJsonPayload." + KEY_INSTRUCTIONS));
        }
        for (Map.Entry<String, JsonNode> field : payload.properties()) {
            if (!SUPPORTED_EXTRA_KEYS.contains(field.getKey())) {
                log.warn("Ignoring unsupported extraJsonPayload key '{}', supported keys are {}", field.getKey(),
                        SUPPORTED_EXTRA_KEYS);
            }
        }
        return ContentIntelligenceJson.optBoolean(payload, KEY_SAVE_RESULT, false);
    }

    /** Accepts an integral JSON number or a string holding one. */
    protected static int toInt(JsonNode value, String option) {
        if (value.isIntegralNumber() && value.canConvertToInt()) {
            return value.intValue();
        }
        if (value.isTextual()) {
            try {
                return Integer.parseInt(value.textValue().trim());
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        throw new IllegalArgumentException(option + " must be an integer");
    }

    protected static String textOrNull(ObjectNode payload, String key) {
        JsonNode value = payload.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isValueNode()) {
            throw new IllegalArgumentException("extraJsonPayload." + key + " must be a string");
        }
        return value.asText();
    }

    protected static Map<String, String> toStringMap(JsonNode node, String option) {
        if (!(node instanceof ObjectNode object)) {
            throw new IllegalArgumentException(option + " must contain JSON objects");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> field : object.properties()) {
            if (!field.getValue().isValueNode() || field.getValue().isNull()) {
                throw new IllegalArgumentException(option + "." + field.getKey() + " must be a string");
            }
            map.put(field.getKey(), field.getValue().asText());
        }
        return map;
    }

    /** Applies the actions and their configuration to a Knowledge Enrichment process request. */
    public void configure(ProcessRequest.Builder builder) {
        for (Action action : actions) {
            builder.action(action, actionConfig);
        }
        if (saveResultInContentLakeRepository) {
            builder.saveResultInContentLakeRepository(true);
        }
    }

    /** @return the API names of the actions, sorted, e.g. to fingerprint the request */
    public List<String> actionNames() {
        return actions.stream().map(Action::value).sorted().toList();
    }
}
