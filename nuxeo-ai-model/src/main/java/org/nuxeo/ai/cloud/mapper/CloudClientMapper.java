/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.cloud.mapper;

import org.nuxeo.ai.cloud.dto.ModelInfo;
import org.nuxeo.ai.cloud.dto.DatasetExportResult;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper to convert between legacy CloudClient objects and abstraction layer DTOs.
 * This isolates the abstraction layer from the underlying implementation details.
 */
public class CloudClientMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parse models from JSONBlob returned by legacy CloudClient
     * @param modelsBlob The JSONBlob containing models data
     * @return List of ModelInfo DTOs
     */
    public static List<ModelInfo> parseModelsFromJSON(JSONBlob modelsBlob) {
        List<ModelInfo> models = new ArrayList<>();

        if (modelsBlob == null) {
            return models;
        }

        try {
            // Parse the JSON content
            String jsonContent = modelsBlob.getString();
            JsonNode rootNode = objectMapper.readTree(jsonContent);

            // Handle different JSON structures that might be returned
            JsonNode modelsArray = null;
            if (rootNode.isArray()) {
                modelsArray = rootNode;
            } else if (rootNode.has("models")) {
                modelsArray = rootNode.get("models");
            } else if (rootNode.has("data")) {
                modelsArray = rootNode.get("data");
            }

            if (modelsArray != null && modelsArray.isArray()) {
                for (JsonNode modelNode : modelsArray) {
                    ModelInfo model = parseModelFromJsonNode(modelNode);
                    if (model != null) {
                        models.add(model);
                    }
                }
            }

        } catch (Exception e) {
            // If parsing fails, return empty list rather than throwing exception
            // This provides graceful degradation
        }

        return models;
    }

    /**
     * Parse a single model from JSON node
     */
    private static ModelInfo parseModelFromJsonNode(JsonNode modelNode) {
        try {
            String modelId = getTextValue(modelNode, "id", "modelId", "model_id");
            String name = getTextValue(modelNode, "name", "modelName", "model_name");
            String type = getTextValue(modelNode, "type", "modelType", "model_type");
            String status = getTextValue(modelNode, "status", "state", "model_status");
            String version = getTextValue(modelNode, "version", "modelVersion", "model_version");
            String trainingStatus = getTextValue(modelNode, "trainingStatus", "training_status", "training_state");
            String description = getTextValue(modelNode, "description", "desc", "model_description");

            Float accuracy = getFloatValue(modelNode, "accuracy", "score", "model_accuracy");

            return new ModelInfo(
                    modelId != null ? modelId : "unknown_" + System.currentTimeMillis(),
                    name != null ? name : "Unknown Model",
                    type != null ? type : "UNKNOWN",
                    status != null ? status : "UNKNOWN",
                    version != null ? version : "1.0",
                    LocalDateTime.now(), // createdAt
                    LocalDateTime.now(), // updatedAt
                    Map.of(), // configuration
                    Map.of(), // metadata
                    trainingStatus != null ? trainingStatus : "UNKNOWN",
                    accuracy,
                    description
            );

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get text value from JSON node with multiple possible field names
     */
    private static String getTextValue(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode field = node.get(fieldName);
            if (field != null && !field.isNull()) {
                return field.asText();
            }
        }
        return null;
    }

    /**
     * Get float value from JSON node with multiple possible field names
     */
    private static Float getFloatValue(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode field = node.get(fieldName);
            if (field != null && !field.isNull() && field.isNumber()) {
                return (float) field.asDouble();
            }
        }
        return null;
    }

    /**
     * Map legacy model object to ModelInfo DTO
     * @param legacyModel The legacy model object from CloudClient
     * @return ModelInfo DTO
     */
    public static ModelInfo mapToModelInfo(Object legacyModel) {
        if (legacyModel == null) {
            return null;
        }

        try {
            // Use reflection to extract properties from legacy model object
            // This handles different legacy model types without tight coupling

            String modelId = extractProperty(legacyModel, "id", "modelId", "getId");
            String name = extractProperty(legacyModel, "name", "modelName", "getName");
            String type = extractProperty(legacyModel, "type", "modelType", "getType");
            String status = extractProperty(legacyModel, "status", "state", "getStatus");
            String version = extractProperty(legacyModel, "version", "modelVersion", "getVersion");
            String trainingStatus = extractProperty(legacyModel, "trainingStatus", "trainingState", "getTrainingStatus");
            String description = extractProperty(legacyModel, "description", "desc", "getDescription");

            Float accuracy = extractNumericProperty(legacyModel, "accuracy", "score", "getAccuracy");

            @SuppressWarnings("unchecked")
            Map<String, Object> configuration = extractMapProperty(legacyModel, "configuration", "config", "getConfiguration");

            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = extractMapProperty(legacyModel, "metadata", "meta", "getMetadata");

            // Use current time if timestamp extraction fails
            LocalDateTime now = LocalDateTime.now();

            return new ModelInfo(
                modelId != null ? modelId : "unknown",
                name != null ? name : "Unknown Model",
                type != null ? type : "UNKNOWN",
                status != null ? status : "UNKNOWN",
                version != null ? version : "1.0",
                now, // createdAt
                now, // updatedAt
                configuration != null ? configuration : Map.of(),
                metadata != null ? metadata : Map.of(),
                trainingStatus != null ? trainingStatus : "UNKNOWN",
                accuracy,
                description
            );

        } catch (Exception e) {
            // Fallback to basic model info if mapping fails
            return new ModelInfo("unknown", "Unknown Model", "UNKNOWN", "ERROR");
        }
    }

    /**
     * Map legacy export status object to DatasetExportResult DTO
     * @param legacyExportStatus The legacy export status object from CloudClient
     * @return DatasetExportResult DTO
     */
    public static DatasetExportResult mapToDatasetExportResult(Object legacyExportStatus) {
        if (legacyExportStatus == null) {
            return null;
        }

        try {
            String exportId = extractProperty(legacyExportStatus, "id", "exportId", "getId");
            String status = extractProperty(legacyExportStatus, "status", "state", "getStatus");
            String location = extractProperty(legacyExportStatus, "location", "exportLocation", "getLocation");
            String format = extractProperty(legacyExportStatus, "format", "exportFormat", "getFormat");
            String errorMessage = extractProperty(legacyExportStatus, "error", "errorMessage", "getError");

            Long totalRecords = extractLongProperty(legacyExportStatus, "totalRecords", "total", "getTotalRecords");
            Long exportedRecords = extractLongProperty(legacyExportStatus, "exportedRecords", "exported", "getExportedRecords");

            return new DatasetExportResult(
                exportId != null ? exportId : "unknown",
                status != null ? status : "UNKNOWN",
                location,
                LocalDateTime.now(), // startTime
                LocalDateTime.now(), // endTime
                totalRecords != null ? totalRecords : 0L,
                exportedRecords != null ? exportedRecords : 0L,
                format,
                null, // columns
                null, // metadata
                errorMessage
            );

        } catch (Exception e) {
            // Fallback to error result if mapping fails
            return new DatasetExportResult("unknown", "Mapping error: " + e.getMessage());
        }
    }

    /**
     * Extract string property from object using multiple possible field names
     */
    private static String extractProperty(Object obj, String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                // Try direct field access
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value != null) {
                    return value.toString();
                }
            } catch (Exception e) {
                // Try method access if field access fails
                try {
                    var method = obj.getClass().getMethod(fieldName);
                    Object value = method.invoke(obj);
                    if (value != null) {
                        return value.toString();
                    }
                } catch (Exception ex) {
                    // Continue to next field name
                }
            }
        }
        return null;
    }

    /**
     * Extract numeric property and convert to Float
     */
    private static Float extractNumericProperty(Object obj, String... fieldNames) {
        String value = extractProperty(obj, fieldNames);
        if (value != null) {
            try {
                return Float.parseFloat(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Extract Long property
     */
    private static Long extractLongProperty(Object obj, String... fieldNames) {
        String value = extractProperty(obj, fieldNames);
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Extract Map property from object
     */
    private static Map<String, Object> extractMapProperty(Object obj, String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) value;
                    return map;
                }
            } catch (Exception e) {
                try {
                    var method = obj.getClass().getMethod(fieldName);
                    Object value = method.invoke(obj);
                    if (value instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) value;
                        return map;
                    }
                } catch (Exception ex) {
                    // Continue to next field name
                }
            }
        }
        return Map.of();
    }
}
