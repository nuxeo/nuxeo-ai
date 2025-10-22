/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.cloud.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Model information DTO for cloud AI models.
 * Provides abstraction over cloud provider-specific model representations.
 */
public class ModelInfo {

    private final String modelId;
    private final String name;
    private final String type;
    private final String status;
    private final String version;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final Map<String, Object> configuration;
    private final Map<String, Object> metadata;
    private final String trainingStatus;
    private final Float accuracy;
    private final String description;

    public ModelInfo(String modelId, String name, String type, String status, String version,
                    LocalDateTime createdAt, LocalDateTime updatedAt, Map<String, Object> configuration,
                    Map<String, Object> metadata, String trainingStatus, Float accuracy, String description) {
        this.modelId = modelId;
        this.name = name;
        this.type = type;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.configuration = configuration;
        this.metadata = metadata;
        this.trainingStatus = trainingStatus;
        this.accuracy = accuracy;
        this.description = description;
    }

    // Simplified constructor for basic model info
    public ModelInfo(String modelId, String name, String type, String status) {
        this(modelId, name, type, status, "1.0", LocalDateTime.now(), LocalDateTime.now(),
             Map.of(), Map.of(), "NOT_TRAINED", null, null);
    }

    public String getModelId() { return modelId; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public String getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Map<String, Object> getConfiguration() { return configuration; }
    public Map<String, Object> getMetadata() { return metadata; }
    public String getTrainingStatus() { return trainingStatus; }
    public Float getAccuracy() { return accuracy; }
    public String getDescription() { return description; }

    public boolean isActive() {
        return "ACTIVE".equals(status) || "READY".equals(status);
    }

    public boolean isTraining() {
        return "TRAINING".equals(trainingStatus) || "IN_PROGRESS".equals(trainingStatus);
    }

    public boolean isTrainingCompleted() {
        return "COMPLETED".equals(trainingStatus) || "SUCCESS".equals(trainingStatus);
    }

    public boolean hasTrainingFailed() {
        return "FAILED".equals(trainingStatus) || "ERROR".equals(trainingStatus);
    }

    @Override
    public String toString() {
        return "ModelInfo{" +
                "modelId='" + modelId + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", status='" + status + '\'' +
                ", version='" + version + '\'' +
                ", trainingStatus='" + trainingStatus + '\'' +
                ", accuracy=" + accuracy +
                '}';
    }
}
