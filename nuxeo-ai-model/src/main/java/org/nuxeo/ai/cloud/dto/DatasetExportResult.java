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
import java.util.List;
import java.util.Map;

/**
 * Dataset export result DTO for cloud operations.
 * Provides abstraction over cloud provider-specific export representations.
 */
public class DatasetExportResult {

    private final String exportId;
    private final String status;
    private final String exportLocation;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final long totalRecords;
    private final long exportedRecords;
    private final String format;
    private final List<String> columns;
    private final Map<String, Object> metadata;
    private final String errorMessage;

    public DatasetExportResult(String exportId, String status, String exportLocation,
                              LocalDateTime startTime, LocalDateTime endTime,
                              long totalRecords, long exportedRecords, String format,
                              List<String> columns, Map<String, Object> metadata,
                              String errorMessage) {
        this.exportId = exportId;
        this.status = status;
        this.exportLocation = exportLocation;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalRecords = totalRecords;
        this.exportedRecords = exportedRecords;
        this.format = format;
        this.columns = columns;
        this.metadata = metadata;
        this.errorMessage = errorMessage;
    }

    // Simplified constructor for successful exports
    public DatasetExportResult(String exportId, String exportLocation, long totalRecords, String format) {
        this(exportId, "COMPLETED", exportLocation, LocalDateTime.now(), LocalDateTime.now(),
             totalRecords, totalRecords, format, null, null, null);
    }

    // Constructor for failed exports
    public DatasetExportResult(String exportId, String errorMessage) {
        this(exportId, "FAILED", null, LocalDateTime.now(), LocalDateTime.now(),
             0, 0, null, null, null, errorMessage);
    }

    public String getExportId() { return exportId; }
    public String getStatus() { return status; }
    public String getExportLocation() { return exportLocation; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public long getTotalRecords() { return totalRecords; }
    public long getExportedRecords() { return exportedRecords; }
    public String getFormat() { return format; }
    public List<String> getColumns() { return columns; }
    public Map<String, Object> getMetadata() { return metadata; }
    public String getErrorMessage() { return errorMessage; }

    public boolean isSuccessful() {
        return "COMPLETED".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    public boolean isInProgress() {
        return "IN_PROGRESS".equals(status) || "PENDING".equals(status);
    }

    public double getCompletionPercentage() {
        if (totalRecords == 0) {
            return 0.0;
        }
        return (double) exportedRecords / totalRecords * 100.0;
    }

    @Override
    public String toString() {
        return "DatasetExportResult{" +
                "exportId='" + exportId + '\'' +
                ", status='" + status + '\'' +
                ", exportLocation='" + exportLocation + '\'' +
                ", totalRecords=" + totalRecords +
                ", exportedRecords=" + exportedRecords +
                ", format='" + format + '\'' +
                ", completionPercentage=" + getCompletionPercentage() + "%" +
                '}';
    }
}
