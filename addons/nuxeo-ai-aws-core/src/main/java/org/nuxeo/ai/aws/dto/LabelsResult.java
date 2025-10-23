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
public record LabelsResult(List<Label> labels) {
    public List<Label> getLabels() { return labels; }
    // equals/hashCode retained via record; override if explicit behavior needed
    @Override public boolean equals(Object o) { return o instanceof LabelsResult lr && Objects.equals(labels, lr.labels); }
    @Override public int hashCode() { return Objects.hash(labels); }
    /**
     * Represents a detected label
     */
    public static record Label(String name, Float confidence, List<String> parents) {
        public Label(String name, float confidence) { this(name, (Float) confidence, List.of()); }
        public String getName() { return name; }
        public Float getConfidence() { return confidence; }
        public List<String> getParents() { return parents; }
        @Override public boolean equals(Object o) { return o instanceof Label l && Objects.equals(name, l.name) && Objects.equals(confidence, l.confidence) && Objects.equals(parents, l.parents); }
        @Override public int hashCode() { return Objects.hash(name, confidence, parents); }
        @Override public String toString() { return "Label{" + "name='" + name + '\'' + ", confidence=" + confidence + ", parents=" + parents + '}'; }
    }
}
