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

    public static record Label(String name, float confidence, List<String> categories) {
        public String getName() { return name; }
        public float getConfidence() { return confidence; }
        public List<String> getCategories() { return categories; }
    }

    public static record Face(float confidence, BoundingBox boundingBox, FaceAttributes attributes) {
        public float getConfidence() { return confidence; }
        public BoundingBox getBoundingBox() { return boundingBox; }
        public FaceAttributes getAttributes() { return attributes; }
    }

    public static record BoundingBox(float left, float top, float width, float height) {
        public float getLeft() { return left; }
        public float getTop() { return top; }
        public float getWidth() { return width; }
        public float getHeight() { return height; }
    }

    public static record FaceAttributes(Integer estimatedAge, String gender, boolean smile, boolean eyeglasses, boolean sunglasses) {
        public Integer getEstimatedAge() { return estimatedAge; }
        public String getGender() { return gender; }
        public boolean isSmile() { return smile; }
        public boolean isEyeglasses() { return eyeglasses; }
        public boolean isSunglasses() { return sunglasses; }
    }

    public static record TextDetection(String detectedText, String type, float confidence, BoundingBox boundingBox) {
        public String getDetectedText() { return detectedText; }
        public String getType() { return type; }
        public float getConfidence() { return confidence; }
        public BoundingBox getBoundingBox() { return boundingBox; }
    }

    public static record ModerationLabel(String name, float confidence, String parentName) {
        public String getName() { return name; }
        public float getConfidence() { return confidence; }
        public String getParentName() { return parentName; }
    }

    public static record Celebrity(String name, float confidence, List<String> urls, BoundingBox boundingBox) {
        public String getName() { return name; }
        public float getConfidence() { return confidence; }
        public List<String> getUrls() { return urls; }
        public BoundingBox getBoundingBox() { return boundingBox; }
    }
}
