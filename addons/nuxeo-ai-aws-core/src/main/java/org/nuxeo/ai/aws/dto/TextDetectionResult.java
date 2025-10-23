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
public record TextDetectionResult(List<TextDetection> textDetections) {
    public List<TextDetection> getTextDetections() { return textDetections; }
    // equals/hashCode from record ok; keep explicit if desired
    @Override public boolean equals(Object o) { return o instanceof TextDetectionResult that && Objects.equals(textDetections, that.textDetections); }
    @Override public int hashCode() { return Objects.hash(textDetections); }

    /**
     * Represents detected text
     */
    public static record TextDetection(String detectedText, String type, Float confidence, BoundingBox boundingBox) {
        // backward compatible overloaded constructor
        public TextDetection(String detectedText, String type, float confidence) { this(detectedText, type, (Float) confidence, null); }
        public String getDetectedText() { return detectedText; }
        public String getType() { return type; }
        public Float getConfidence() { return confidence; }
        public BoundingBox getBoundingBox() { return boundingBox; }
    }
    /**
     * Represents a bounding box for detected text
     */
    public static record BoundingBox(Float width, Float height, Float left, Float top) {
        public Float getWidth() { return width; }
        public Float getHeight() { return height; }
        public Float getLeft() { return left; }
        public Float getTop() { return top; }
    }
}
