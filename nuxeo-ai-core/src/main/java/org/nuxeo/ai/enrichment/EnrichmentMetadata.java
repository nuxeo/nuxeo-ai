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

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.nuxeo.ai.metadata.AIMetadata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * A normalized view of the result of an Enrichment Service.
 * This class is designed to be serialized as JSON.
 */
@JsonDeserialize(builder = EnrichmentMetadata.Builder.class)
public class EnrichmentMetadata extends AIMetadata {

    protected final List<String> targetDocumentProperties; //Document reference
    protected final List<Label> labels;
    protected final String blobDigest;

    private EnrichmentMetadata(Builder builder) {
        super(builder.serviceName, builder.kind, builder.repositoryName, builder.targetDocumentRef,
              builder.creator, builder.created, builder.rawKey
        );
        if (builder.labels == null || builder.labels.isEmpty()) {
            labels = emptyList();
        } else {
            labels = unmodifiableList(builder.labels);
        }
        blobDigest = builder.blobDigest;
        if (builder.targetDocumentProperties != null && !builder.targetDocumentProperties.isEmpty()) {
            targetDocumentProperties = builder.targetDocumentProperties;
        } else {
            targetDocumentProperties = emptyList();
        }
    }

    public List<Label> getLabels() {
        return labels;
    }

    public String getBlobDigest() {
        return blobDigest;
    }

    @JsonIgnore
    public boolean isSingleLabel() {
        return labels.size() == 1;
    }

    public List<String> getTargetDocumentProperties() {
        return targetDocumentProperties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        EnrichmentMetadata metadata = (EnrichmentMetadata) o;
        return Objects.equals(targetDocumentProperties, metadata.targetDocumentProperties) &&
                Objects.equals(labels, metadata.labels) &&
                Objects.equals(blobDigest, metadata.blobDigest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), targetDocumentProperties, labels, blobDigest);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("targetDocumentProperties", targetDocumentProperties)
                .append("labels", labels)
                .append("blobDigest", blobDigest)
                .append("created", created)
                .append("creator", creator)
                .append("serviceName", serviceName)
                .append("kind", kind)
                .append("repositoryName", repositoryName)
                .append("targetDocumentRef", targetDocumentRef)
                .append("rawKey", rawKey)
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
            return new ToStringBuilder(this)
                    .append("name", name)
                    .append("confidence", confidence)
                    .toString();
        }
    }

    public static class Builder {

        //mandatory
        private final Instant created;
        private final String serviceName;
        private final String kind;
        private final String repositoryName;
        private final String targetDocumentRef;

        //optional
        private String rawKey;
        private List<Label> labels;
        private String creator;
        private String blobDigest;
        private List<String> targetDocumentProperties;

        @JsonCreator
        public Builder(@JsonProperty("created") Instant created,
                       @JsonProperty("kind") String kind,
                       @JsonProperty("serviceName") String serviceName,
                       @JsonProperty("repositoryName") String repositoryName,
                       @JsonProperty("targetDocumentRef") String targetDocumentRef) {
            this.created = created;
            this.kind = kind;
            this.serviceName = serviceName;
            this.repositoryName = repositoryName;
            this.targetDocumentRef = targetDocumentRef;
        }

        public Builder(String kind, String serviceName, String repositoryName, String targetDocumentRef) {
            this.repositoryName = repositoryName;
            this.targetDocumentRef = targetDocumentRef;
            this.created = Instant.now();
            this.kind = kind;
            this.serviceName = serviceName;
        }

        public Builder withTargetDocumentProperties(List<String> targetDocumentProperty) {
            this.targetDocumentProperties = targetDocumentProperty;
            return this;
        }

        public Builder withLabels(List<Label> labels) {
            this.labels = labels;
            return this;
        }

        public Builder withCreator(String creator) {
            this.creator = creator;
            return this;
        }

        public Builder withRawKey(String rawBlobKey) {
            this.rawKey = rawBlobKey;
            return this;
        }

        public Builder withBlobDigest(String blobDigest) {
            this.blobDigest = blobDigest;
            return this;
        }

        public EnrichmentMetadata build() {
            if (StringUtils.isBlank(serviceName)
                    || StringUtils.isBlank(kind)
                    || StringUtils.isBlank(targetDocumentRef)
                    || StringUtils.isBlank(repositoryName)
                    || created == null) {
                throw new IllegalArgumentException("Invalid Enrichment metadata has been given. " + this.toString());
            }
            return new EnrichmentMetadata(this);
        }
    }
}
