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

public record LabelsResult(List<Label> labels) {

    // Optional: enforce immutability
    public LabelsResult {
        labels = List.copyOf(labels);
    }

    public static record Label(String name, Float confidence, List<String> parents) {

        public Label(String name, float confidence) {
            this(name, confidence, List.of());
        }

        public Label {
            parents = List.copyOf(parents);
        }
    }
}
