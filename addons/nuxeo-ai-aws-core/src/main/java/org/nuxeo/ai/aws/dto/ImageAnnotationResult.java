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
public class ImageAnnotationResult {

    private final List<Annotation> annotations;
    private final String imageId;
    private final float confidence;

    public ImageAnnotationResult(List<Annotation> annotations, String imageId, float confidence) {
        this.annotations = annotations;
        this.imageId = imageId;
        this.confidence = confidence;
    }

    public List<Annotation> getAnnotations() { return annotations; }
    public String getImageId() { return imageId; }
    public float getConfidence() { return confidence; }

    public static class Annotation {
        private final String category;
        private final String description;
        private final float score;
        private final BoundingBox boundingBox;

        public Annotation(String category, String description, float score, BoundingBox boundingBox) {
            this.category = category;
            this.description = description;
            this.score = score;
            this.boundingBox = boundingBox;
        }

        public String getCategory() { return category; }
        public String getDescription() { return description; }
        public float getScore() { return score; }
        public BoundingBox getBoundingBox() { return boundingBox; }

        @Override
        public String toString() {
            return "Annotation{" +
                    "category='" + category + '\'' +
                    ", description='" + description + '\'' +
                    ", score=" + score +
                    ", boundingBox=" + boundingBox +
                    '}';
        }
    }

    public static class BoundingBox {
        private final float left, top, width, height;

        public BoundingBox(float left, float top, float width, float height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }

        public float getLeft() { return left; }
        public float getTop() { return top; }
        public float getWidth() { return width; }
        public float getHeight() { return height; }

        @Override
        public String toString() {
            return "BoundingBox{" +
                    "left=" + left +
                    ", top=" + top +
                    ", width=" + width +
                    ", height=" + height +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "ImageAnnotationResult{" +
                "annotations=" + annotations +
                ", imageId='" + imageId + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}
