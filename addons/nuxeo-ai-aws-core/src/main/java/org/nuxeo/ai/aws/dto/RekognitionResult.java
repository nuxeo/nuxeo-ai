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
 * AWS SDK-independent result DTOs for Rekognition operations.
 * These DTOs are returned by our abstraction layer.
 */
public class RekognitionResult {

    public static class Label {
        private final String name;
        private final float confidence;
        private final List<String> categories;

        public Label(String name, float confidence, List<String> categories) {
            this.name = name;
            this.confidence = confidence;
            this.categories = categories;
        }

        public String getName() { return name; }
        public float getConfidence() { return confidence; }
        public List<String> getCategories() { return categories; }
    }

    public static class Face {
        private final float confidence;
        private final BoundingBox boundingBox;
        private final FaceAttributes attributes;

        public Face(float confidence, BoundingBox boundingBox, FaceAttributes attributes) {
            this.confidence = confidence;
            this.boundingBox = boundingBox;
            this.attributes = attributes;
        }

        public float getConfidence() { return confidence; }
        public BoundingBox getBoundingBox() { return boundingBox; }
        public FaceAttributes getAttributes() { return attributes; }
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
    }

    public static class FaceAttributes {
        private final Integer estimatedAge;
        private final String gender;
        private final boolean smile;
        private final boolean eyeglasses;
        private final boolean sunglasses;

        public FaceAttributes(Integer estimatedAge, String gender, boolean smile, boolean eyeglasses, boolean sunglasses) {
            this.estimatedAge = estimatedAge;
            this.gender = gender;
            this.smile = smile;
            this.eyeglasses = eyeglasses;
            this.sunglasses = sunglasses;
        }

        public Integer getEstimatedAge() { return estimatedAge; }
        public String getGender() { return gender; }
        public boolean isSmile() { return smile; }
        public boolean isEyeglasses() { return eyeglasses; }
        public boolean isSunglasses() { return sunglasses; }
    }

    public static class TextDetection {
        private final String detectedText;
        private final String type;
        private final float confidence;
        private final BoundingBox boundingBox;

        public TextDetection(String detectedText, String type, float confidence, BoundingBox boundingBox) {
            this.detectedText = detectedText;
            this.type = type;
            this.confidence = confidence;
            this.boundingBox = boundingBox;
        }

        public String getDetectedText() { return detectedText; }
        public String getType() { return type; }
        public float getConfidence() { return confidence; }
        public BoundingBox getBoundingBox() { return boundingBox; }
    }

    public static class ModerationLabel {
        private final String name;
        private final float confidence;
        private final String parentName;

        public ModerationLabel(String name, float confidence, String parentName) {
            this.name = name;
            this.confidence = confidence;
            this.parentName = parentName;
        }

        public String getName() { return name; }
        public float getConfidence() { return confidence; }
        public String getParentName() { return parentName; }
    }

    public static class Celebrity {
        private final String name;
        private final float confidence;
        private final List<String> urls;
        private final BoundingBox boundingBox;

        public Celebrity(String name, float confidence, List<String> urls, BoundingBox boundingBox) {
            this.name = name;
            this.confidence = confidence;
            this.urls = urls;
            this.boundingBox = boundingBox;
        }

        public String getName() { return name; }
        public float getConfidence() { return confidence; }
        public List<String> getUrls() { return urls; }
        public BoundingBox getBoundingBox() { return boundingBox; }
    }
}
