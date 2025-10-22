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
package org.nuxeo.ai.aws.dto;

import java.util.List;
import java.util.Objects;

/**
 * Domain-specific DTO for Rekognition text detection results.
 */
public class TextDetectionResult {

    private final List<TextDetection> textDetections;

    public TextDetectionResult(List<TextDetection> textDetections) {
        this.textDetections = textDetections;
    }

    public List<TextDetection> getTextDetections() {
        return textDetections;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextDetectionResult that = (TextDetectionResult) o;
        return Objects.equals(textDetections, that.textDetections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(textDetections);
    }

    /**
     * Represents detected text
     */
    public static class TextDetection {
        private final String detectedText;
        private final String type;
        private final Float confidence;
        private final BoundingBox boundingBox;

        public TextDetection(String detectedText, String type, Float confidence, BoundingBox boundingBox) {
            this.detectedText = detectedText;
            this.type = type;
            this.confidence = confidence;
            this.boundingBox = boundingBox;
        }

        // Overloaded constructor for backward compatibility
        public TextDetection(String detectedText, String type, float confidence) {
            this(detectedText, type, (Float) confidence, null);
        }

        public String getDetectedText() {
            return detectedText;
        }

        public String getType() {
            return type;
        }

        public Float getConfidence() {
            return confidence;
        }

        public BoundingBox getBoundingBox() {
            return boundingBox;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TextDetection that = (TextDetection) o;
            return Objects.equals(detectedText, that.detectedText) &&
                   Objects.equals(type, that.type) &&
                   Objects.equals(confidence, that.confidence) &&
                   Objects.equals(boundingBox, that.boundingBox);
        }

        @Override
        public int hashCode() {
            return Objects.hash(detectedText, type, confidence, boundingBox);
        }
    }

    /**
     * Represents a bounding box for detected text
     */
    public static class BoundingBox {
        private final Float width;
        private final Float height;
        private final Float left;
        private final Float top;

        public BoundingBox(Float width, Float height, Float left, Float top) {
            this.width = width;
            this.height = height;
            this.left = left;
            this.top = top;
        }

        public Float getWidth() {
            return width;
        }

        public Float getHeight() {
            return height;
        }

        public Float getLeft() {
            return left;
        }

        public Float getTop() {
            return top;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BoundingBox that = (BoundingBox) o;
            return Objects.equals(width, that.width) &&
                   Objects.equals(height, that.height) &&
                   Objects.equals(left, that.left) &&
                   Objects.equals(top, that.top);
        }

        @Override
        public int hashCode() {
            return Objects.hash(width, height, left, top);
        }
    }
}
