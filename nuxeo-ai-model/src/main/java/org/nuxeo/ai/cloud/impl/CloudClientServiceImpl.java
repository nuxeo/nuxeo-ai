/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.cloud.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.cloud.abstraction.CloudClientService;
import org.nuxeo.ai.cloud.dto.ModelInfo;
import org.nuxeo.ai.cloud.dto.DatasetExportResult;
import org.nuxeo.ai.cloud.mapper.CloudClientMapper;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of CloudClientService that wraps the legacy CloudClient.
 * This provides abstraction over the existing Nuxeo Cloud AI functionality.
 */
public class CloudClientServiceImpl extends DefaultComponent implements CloudClientService {

    private static final Log log = LogFactory.getLog(CloudClientServiceImpl.class);

    // In-memory storage for demonstration (in production, this would use proper persistence)
    private final Map<String, ModelInfo> modelsCache = new ConcurrentHashMap<>();
    private final Map<String, DatasetExportResult> exportsCache = new ConcurrentHashMap<>();
    private final Map<String, String> datasetCache = new ConcurrentHashMap<>();

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        if (log.isDebugEnabled()) {
            log.debug("CloudClientServiceImpl started");
        }
    }

    @Override
    public List<ModelInfo> getModels() {
        try {
            CloudClient legacyClient = Framework.getService(CloudClient.class);
            if (legacyClient == null) {
                log.warn("CloudClient service not available");
                return new ArrayList<>(modelsCache.values());
            }

            // Get current session (in production, this would be passed as parameter)
            CoreSession session = getCurrentSession();
            if (session == null) {
                log.warn("No CoreSession available, returning cached models");
                return new ArrayList<>(modelsCache.values());
            }

            // Get models from cloud and parse JSON response
            JSONBlob modelsBlob = legacyClient.getAllModels(session);
            List<ModelInfo> models = CloudClientMapper.parseModelsFromJSON(modelsBlob);

            // Update cache
            models.forEach(model -> modelsCache.put(model.getModelId(), model));

            return models;

        } catch (IOException e) {
            log.error("Error retrieving models from cloud", e);
            return new ArrayList<>(modelsCache.values());
        } catch (Exception e) {
            log.error("Unexpected error retrieving models", e);
            return new ArrayList<>(modelsCache.values());
        }
    }

    @Override
    public ModelInfo getModel(String modelId) {
        // First check cache
        ModelInfo cachedModel = modelsCache.get(modelId);
        if (cachedModel != null) {
            return cachedModel;
        }

        // If not in cache, refresh all models and try again
        List<ModelInfo> allModels = getModels();
        return allModels.stream()
                .filter(model -> modelId.equals(model.getModelId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public ModelInfo createModel(String name, String type, Map<String, Object> configuration) {
        // Since the legacy CloudClient doesn't have createModel, we simulate it
        String modelId = generateModelId();

        ModelInfo newModel = new ModelInfo(
                modelId,
                name,
                type,
                "CREATING",
                "1.0",
                LocalDateTime.now(),
                LocalDateTime.now(),
                configuration != null ? configuration : Map.of(),
                Map.of("createdBy", "CloudClientService"),
                "NOT_TRAINED",
                null,
                "Model created via abstraction layer"
        );

        modelsCache.put(modelId, newModel);

        if (log.isDebugEnabled()) {
            log.debug("Created model: " + modelId + " with name: " + name);
        }

        return newModel;
    }

    @Override
    public ModelInfo updateModel(String modelId, Map<String, Object> updates) {
        ModelInfo existingModel = modelsCache.get(modelId);
        if (existingModel == null) {
            throw new NuxeoException("Model not found: " + modelId);
        }

        // Create updated model (ModelInfo is immutable, so we create a new instance)
        Map<String, Object> newConfiguration = new HashMap<>(existingModel.getConfiguration());
        Map<String, Object> newMetadata = new HashMap<>(existingModel.getMetadata());

        // Apply updates
        updates.forEach((key, value) -> {
            if ("configuration".equals(key) && value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> configUpdates = (Map<String, Object>) value;
                newConfiguration.putAll(configUpdates);
            } else if ("metadata".equals(key) && value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metaUpdates = (Map<String, Object>) value;
                newMetadata.putAll(metaUpdates);
            }
        });

        ModelInfo updatedModel = new ModelInfo(
                existingModel.getModelId(),
                existingModel.getName(),
                existingModel.getType(),
                existingModel.getStatus(),
                existingModel.getVersion(),
                existingModel.getCreatedAt(),
                LocalDateTime.now(), // Update timestamp
                newConfiguration,
                newMetadata,
                existingModel.getTrainingStatus(),
                existingModel.getAccuracy(),
                existingModel.getDescription()
        );

        modelsCache.put(modelId, updatedModel);

        if (log.isDebugEnabled()) {
            log.debug("Updated model: " + modelId);
        }

        return updatedModel;
    }

    @Override
    public void deleteModel(String modelId) {
        ModelInfo removed = modelsCache.remove(modelId);
        if (removed != null && log.isDebugEnabled()) {
            log.debug("Deleted model: " + modelId);
        }
    }

    @Override
    public DatasetExportResult startDatasetExport(String datasetId, String format, Map<String, Object> options) {
        try {
            CloudClient legacyClient = Framework.getService(CloudClient.class);
            if (legacyClient == null) {
                throw new NuxeoException("CloudClient service not available");
            }

            CoreSession session = getCurrentSession();
            if (session == null) {
                throw new NuxeoException("No CoreSession available");
            }

            // Create export ID
            String exportId = generateExportId();

            // Create initial export result
            DatasetExportResult exportResult = new DatasetExportResult(
                    exportId,
                    "IN_PROGRESS",
                    null, // location will be set when complete
                    LocalDateTime.now(),
                    null, // endTime will be set when complete
                    0L, // will be updated based on actual dataset
                    0L,
                    format,
                    null,
                    options,
                    null
            );

            exportsCache.put(exportId, exportResult);

            // In a real implementation, you would start an async process here
            // For now, we simulate immediate completion
            simulateExportCompletion(exportId, datasetId, format);

            return exportResult;

        } catch (Exception e) {
            log.error("Error starting dataset export", e);
            throw new NuxeoException("Failed to start dataset export", e);
        }
    }

    @Override
    public DatasetExportResult getExportStatus(String exportId) {
        return exportsCache.get(exportId);
    }

    @Override
    public String uploadDataset(String name, byte[] data, String format) {
        try {
            CloudClient legacyClient = Framework.getService(CloudClient.class);
            if (legacyClient == null) {
                throw new NuxeoException("CloudClient service not available");
            }

            // The legacy CloudClient expects a DocumentModel for uploadDataset
            // We need to create a DocumentModel from the provided data
            CoreSession session = getCurrentSession();
            if (session == null) {
                throw new NuxeoException("No CoreSession available");
            }

            // Create a temporary document for the dataset
            // In a real implementation, you would properly create a dataset document
            DocumentModel datasetDoc = session.createDocumentModel("/", name, "File");
            datasetDoc.setPropertyValue("dc:title", name);
            datasetDoc = session.createDocument(datasetDoc);

            // Use the legacy client method
            String datasetId = legacyClient.uploadDataset(datasetDoc);

            // Cache the dataset info
            datasetCache.put(datasetId, name);

            if (log.isDebugEnabled()) {
                log.debug("Uploaded dataset: " + datasetId + " with name: " + name);
            }

            return datasetId;

        } catch (Exception e) {
            log.error("Error uploading dataset", e);
            throw new NuxeoException("Failed to upload dataset", e);
        }
    }

    @Override
    public Map<String, Object> getDatasetInfo(String datasetId) {
        String name = datasetCache.get(datasetId);
        Map<String, Object> info = new HashMap<>();
        info.put("id", datasetId);
        info.put("name", name != null ? name : "Unknown Dataset");
        info.put("status", "AVAILABLE");
        return info;
    }

    @Override
    public ModelInfo trainModel(String modelId, String datasetId, Map<String, Object> trainingParams) {
        ModelInfo model = modelsCache.get(modelId);
        if (model == null) {
            throw new NuxeoException("Model not found: " + modelId);
        }

        // Create updated model with training status
        ModelInfo trainingModel = new ModelInfo(
                model.getModelId(),
                model.getName(),
                model.getType(),
                "TRAINING",
                model.getVersion(),
                model.getCreatedAt(),
                LocalDateTime.now(),
                model.getConfiguration(),
                model.getMetadata(),
                "IN_PROGRESS",
                null, // accuracy will be set when training completes
                model.getDescription()
        );

        modelsCache.put(modelId, trainingModel);

        // In a real implementation, you would start training process here
        // For now, we simulate training completion
        simulateTrainingCompletion(modelId);

        if (log.isDebugEnabled()) {
            log.debug("Started training for model: " + modelId + " with dataset: " + datasetId);
        }

        return trainingModel;
    }

    @Override
    public ModelInfo getTrainingStatus(String modelId) {
        return modelsCache.get(modelId);
    }

    // Helper methods

    private CoreSession getCurrentSession() {
        // In a real implementation, you would get the session from context
        // This is a placeholder for demonstration
        return null;
    }

    private String generateModelId() {
        return "model_" + System.currentTimeMillis();
    }

    private String generateExportId() {
        return "export_" + System.currentTimeMillis();
    }

    private void simulateExportCompletion(String exportId, String datasetId, String format) {
        // Simulate async completion
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Simulate processing time

                DatasetExportResult completedResult = new DatasetExportResult(
                        exportId,
                        "COMPLETED",
                        "/exports/" + exportId + "." + format.toLowerCase(),
                        LocalDateTime.now().minusSeconds(1),
                        LocalDateTime.now(),
                        100L, // total records
                        100L, // exported records
                        format,
                        null,
                        Map.of("datasetId", datasetId),
                        null
                );

                exportsCache.put(exportId, completedResult);

                if (log.isDebugEnabled()) {
                    log.debug("Export completed: " + exportId);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void simulateTrainingCompletion(String modelId) {
        // Simulate async training completion
        new Thread(() -> {
            try {
                Thread.sleep(5000); // Simulate training time

                ModelInfo model = modelsCache.get(modelId);
                if (model != null) {
                    ModelInfo completedModel = new ModelInfo(
                            model.getModelId(),
                            model.getName(),
                            model.getType(),
                            "ACTIVE",
                            model.getVersion(),
                            model.getCreatedAt(),
                            LocalDateTime.now(),
                            model.getConfiguration(),
                            model.getMetadata(),
                            "COMPLETED",
                            0.85f, // simulated accuracy
                            model.getDescription()
                    );

                    modelsCache.put(modelId, completedModel);

                    if (log.isDebugEnabled()) {
                        log.debug("Training completed for model: " + modelId);
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
