/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Gethin James
 */
package org.nuxeo.ai.enrichment;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * The result of enrichment
 */
public class EnrichmentResult {

    protected List<Label> labels;

    public EnrichmentResult(List<Label> labels) {
        this.labels = labels;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("EnrichmentResult{");
        sb.append("labels=").append(labels);
        sb.append('}');
        return sb.toString();
    }

    public static class Label implements Serializable {

        private final String name;
        private final float confidence;

        public Label(String name, float confidence) {
            this.name = name;
            this.confidence = confidence;
        }

        public String getName() {
            return name;
        }

        public float getConfidence() {
            return confidence;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Label label = (Label) o;
            return Float.compare(label.confidence, confidence) == 0 &&
                    Objects.equals(name, label.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, confidence);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Label{");
            sb.append("name='").append(name).append('\'');
            sb.append(", confidence=").append(confidence);
            sb.append('}');
            return sb.toString();
        }
    }
}
