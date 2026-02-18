/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.aws.dto;

import java.util.List;

/**
 * AWS SDK-independent result DTO for image annotation operations.
 */
public record ImageAnnotationResult(List<Annotation> annotations, String imageId, float confidence) {

    // Optional: enforce immutability of the list
    public ImageAnnotationResult {
        annotations = List.copyOf(annotations);
    }

    public static record Annotation(String category, String description, float score, BoundingBox boundingBox) {
    }

    public static record BoundingBox(float left, float top, float width, float height) {
    }
}
