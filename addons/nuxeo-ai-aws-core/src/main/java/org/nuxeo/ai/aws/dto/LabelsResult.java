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
 * Domain-specific DTO for Rekognition label detection results.
 */
public class LabelsResult {

    private final List<Label> labels;

    public LabelsResult(List<Label> labels) {
        this.labels = labels;
    }

    public List<Label> getLabels() {
        return labels;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabelsResult that = (LabelsResult) o;
        return Objects.equals(labels, that.labels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labels);
    }

    /**
     * Represents a detected label
     */
    public static class Label {
        private final String name;
        private final Float confidence;
        private final List<String> parents;

        public Label(String name, Float confidence, List<String> parents) {
            this.name = name;
            this.confidence = confidence;
            this.parents = parents;
        }

        // Overloaded constructor for backward compatibility
        public Label(String name, float confidence) {
            this(name, (Float) confidence, List.of());
        }

        public String getName() {
            return name;
        }

        public Float getConfidence() {
            return confidence;
        }

        public List<String> getParents() {
            return parents;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Label label = (Label) o;
            return Objects.equals(name, label.name) &&
                   Objects.equals(confidence, label.confidence) &&
                   Objects.equals(parents, label.parents);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, confidence, parents);
        }

        @Override
        public String toString() {
            return "Label{" +
                    "name='" + name + '\'' +
                    ", confidence=" + confidence +
                    ", parents=" + parents +
                    '}';
        }
    }
}
