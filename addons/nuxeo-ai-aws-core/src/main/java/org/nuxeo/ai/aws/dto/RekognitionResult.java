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
 * AWS SDK-independent result DTOs for Rekognition operations. These DTOs are returned by our abstraction layer.
 */
public class RekognitionResult {

    public static record Label(String name, float confidence, List<String> categories) {
        public Label {
            categories = List.copyOf(categories);
        }
    }

    public static record Face(float confidence, BoundingBox boundingBox, FaceAttributes attributes) {
    }

    public static record BoundingBox(float left, float top, float width, float height) {
    }

    public static record FaceAttributes(Integer estimatedAge, String gender, boolean smile, boolean eyeglasses,
            boolean sunglasses) {
    }

    public static record TextDetection(String detectedText, String type, float confidence, BoundingBox boundingBox) {
    }

    public static record ModerationLabel(String name, float confidence, String parentName) {
    }

    public static record Celebrity(String name, float confidence, List<String> urls, BoundingBox boundingBox) {
        public Celebrity {
            urls = List.copyOf(urls);
        }
    }
}
