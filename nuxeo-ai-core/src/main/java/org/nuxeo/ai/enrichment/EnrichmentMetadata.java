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
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.runtime.stream.pipes.events.JacksonUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * The result of enrichment
 */
@JsonDeserialize(builder = EnrichmentMetadata.Builder.class)
public class EnrichmentMetadata extends AIMetadata {

    protected final String targetDocumentProperty; //Document reference
    protected final List<Label> labels;
    protected final String blobDigest;
    protected final boolean singleLabel;
    //Is this a single-label result of categorizing instances into precisely one label

    private EnrichmentMetadata(Builder builder) {
        super(builder.predictionModelVersion, builder.repositoryName, builder.targetDocumentRef, builder.human, builder.creator, builder.created, builder.raw);
        if (builder.labels == null || builder.labels.isEmpty()) {
            labels = Collections.emptyList();
        } else {
            labels = Collections.unmodifiableList(builder.labels);
        }
        blobDigest = builder.blobDigest;
        singleLabel = builder.singleLabel;
        targetDocumentProperty = builder.targetDocumentProperty;
    }

    public List<Label> getLabels() {
        return labels;
    }

    public String getBlobDigest() {
        return blobDigest;
    }

    public boolean isSingleLabel() {
        return singleLabel;
    }

    public String getTargetDocumentProperty() {
        return targetDocumentProperty;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnrichmentMetadata metadata = (EnrichmentMetadata) o;
        return singleLabel == metadata.singleLabel &&
                Objects.equals(targetDocumentProperty, metadata.targetDocumentProperty) &&
                Objects.equals(labels, metadata.labels) &&
                Objects.equals(blobDigest, metadata.blobDigest);
    }

    @Override
    public int hashCode() {

        return Objects.hash(targetDocumentProperty, labels, blobDigest, singleLabel);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("targetDocumentProperty", targetDocumentProperty)
                .append("labels", labels)
                .append("blobDigest", blobDigest)
                .append("singleLabel", singleLabel)
                .append("created", created)
                .append("creator", creator)
                .append("raw", raw)
                .append("predictionModelVersion", predictionModelVersion)
                .append("human", human)
                .append("targetDocumentRef", targetDocumentRef)
                .toString();
    }

    public static class Label implements Serializable {

        private final String name;
        private final float confidence;

        @JsonCreator
        public Label(@JsonProperty("name") String name, @JsonProperty("confidence") float confidence) {
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

    public static class Builder {

        //mandatory
        private final Instant created;
        private final String creator;
        private final String predictionModelVersion;
        private final String repositoryName;
        private final String targetDocumentRef;

        //optional
        private String raw;
        private boolean human;
        private List<Label> labels;
        private String blobDigest;
        private boolean singleLabel;
        private String targetDocumentProperty;


        @JsonCreator
        public Builder(@JsonProperty("created") Instant created,
                       @JsonProperty("creator") String creator,
                       @JsonProperty("predictionModelVersion") String predictionModelVersion,
                       @JsonProperty("repositoryName") String repositoryName,
                       @JsonProperty("targetDocumentRef") String targetDocumentRef) {
            this.created = created;
            this.creator = creator;
            this.predictionModelVersion = predictionModelVersion;
            this.repositoryName = repositoryName;
            this.targetDocumentRef = targetDocumentRef;
        }

        public Builder(String creator, String predictionModelVersion, String repositoryName, String targetDocumentRef) {
            this.repositoryName = repositoryName;
            this.targetDocumentRef = targetDocumentRef;
            this.created = Instant.now();
            this.creator = creator;
            this.predictionModelVersion = predictionModelVersion;
        }

        public Builder withTargetDocumentProperty(String targetDocumentProperty) {
            this.targetDocumentProperty = targetDocumentProperty;
            return this;
        }

        @JsonDeserialize(using = JacksonUtil.JsonRawValueDeserializer.class)
        public Builder withRaw(String raw) {
            this.raw = raw;
            return this;
        }

        public Builder withHuman(boolean human) {
            this.human = human;
            return this;
        }

        public Builder withSingleLabel(boolean singleLabel) {
            this.singleLabel = singleLabel;
            return this;
        }

        public Builder withLabels(List<Label> labels) {
            this.labels = labels;
            return this;
        }

        public Builder withBlobDigest(String blobDigest) {
            this.blobDigest = blobDigest;
            return this;
        }

        public EnrichmentMetadata build() {
            if (StringUtils.isBlank(predictionModelVersion)
                    || StringUtils.isBlank(targetDocumentRef)
                    || StringUtils.isBlank(repositoryName)
                    || StringUtils.isBlank(creator)
                    || created == null) {
                throw new IllegalArgumentException("Invalid Enrichment metadata has been given. " + this.toString());
            }
            return new EnrichmentMetadata(this);
        }
    }
}
