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
package org.nuxeo.ai.cloud.abstraction;

import org.nuxeo.ai.cloud.dto.ModelInfo;
import org.nuxeo.ai.cloud.dto.DatasetExportResult;

import java.util.List;
import java.util.Map;

/**
 * Cloud Client Service abstraction interface.
 * Provides a clean API for cloud AI model operations without exposing underlying implementation details.
 * This interface isolates the business logic from specific cloud provider SDKs.
 */
public interface CloudClientService {

    /**
     * Retrieve all available models
     * @return List of model information
     */
    List<ModelInfo> getModels();

    /**
     * Get specific model information
     * @param modelId The unique identifier of the model
     * @return ModelInfo object or null if not found
     */
    ModelInfo getModel(String modelId);

    /**
     * Create a new model
     * @param name The name of the model
     * @param type The type/category of the model
     * @param configuration Model configuration parameters
     * @return Created ModelInfo object
     */
    ModelInfo createModel(String name, String type, Map<String, Object> configuration);

    /**
     * Update an existing model
     * @param modelId The unique identifier of the model
     * @param updates Map of updates to apply
     * @return Updated ModelInfo object
     */
    ModelInfo updateModel(String modelId, Map<String, Object> updates);

    /**
     * Delete a model
     * @param modelId The unique identifier of the model to delete
     */
    void deleteModel(String modelId);

    /**
     * Start dataset export operation
     * @param datasetId The unique identifier of the dataset
     * @param format The export format (e.g., "JSON", "CSV", "JSONL")
     * @param options Additional export options
     * @return DatasetExportResult with export details
     */
    DatasetExportResult startDatasetExport(String datasetId, String format, Map<String, Object> options);

    /**
     * Get export operation status
     * @param exportId The unique identifier of the export operation
     * @return DatasetExportResult with current status
     */
    DatasetExportResult getExportStatus(String exportId);

    /**
     * Upload a dataset for training
     * @param name The name of the dataset
     * @param data The dataset content as bytes
     * @param format The format of the dataset
     * @return The unique identifier of the uploaded dataset
     */
    String uploadDataset(String name, byte[] data, String format);

    /**
     * Get dataset information
     * @param datasetId The unique identifier of the dataset
     * @return Map containing dataset metadata
     */
    Map<String, Object> getDatasetInfo(String datasetId);

    /**
     * Start model training
     * @param modelId The unique identifier of the model to train
     * @param datasetId The unique identifier of the training dataset
     * @param trainingParams Training configuration parameters
     * @return Updated ModelInfo with training status
     */
    ModelInfo trainModel(String modelId, String datasetId, Map<String, Object> trainingParams);

    /**
     * Get training status for a model
     * @param modelId The unique identifier of the model
     * @return ModelInfo with current training status
     */
    ModelInfo getTrainingStatus(String modelId);
}
