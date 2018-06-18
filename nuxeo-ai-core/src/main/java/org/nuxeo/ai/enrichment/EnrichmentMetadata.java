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
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableList;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ecm.core.blob.BlobMeta;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;

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

    private static final long serialVersionUID = -8838535848960975096L;
    private final List<Label> labels;

    private EnrichmentMetadata(Builder builder) {
        super(builder.serviceName, builder.kind, builder.context,
              builder.creator, builder.created, builder.rawKey
        );
        labels = unmodifiableList(builder.labels);
    }

    public List<Label> getLabels() {
        return labels;
    }

    @JsonIgnore
    public boolean isSingleLabel() {
        return labels.size() == 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        EnrichmentMetadata metadata = (EnrichmentMetadata) o;
        return Objects.equals(labels, metadata.labels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), labels);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("labels", labels)
                .append("created", created)
                .append("creator", creator)
                .append("serviceName", serviceName)
                .append("kind", kind)
                .append("context", context)
                .append("rawKey", rawKey)
                .toString();
    }

    public static class Label implements Serializable {

        private static final long serialVersionUID = 8838956163616827139L;
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
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
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

        //Context
        private final String repositoryName;
        private final String documentRef;
        private Set<String> documentProperties;
        private Map<String, String> properties;
        private Context context;

        //optional
        private String rawKey;
        private List<Label> labels;
        private String creator;
        private String blobDigest;

        @JsonCreator
        public Builder(@JsonProperty("created") Instant created,
                       @JsonProperty("kind") String kind,
                       @JsonProperty("serviceName") String serviceName,
                       @JsonProperty("context") Context context) {
            this.created = created;
            this.kind = kind;
            this.serviceName = serviceName;
            if (context == null) {
                throw new IllegalArgumentException("You must specify a valid context.");
            }
            this.context = context;
            this.repositoryName = context.repositoryName;
            this.documentRef = context.documentRef;
        }

        public Builder(String kind, String serviceName, BlobTextStream blobTextStream) {
            this.created = Instant.now();
            this.kind = kind;
            this.serviceName = serviceName;
            this.repositoryName = blobTextStream.getRepositoryName();
            this.documentRef = blobTextStream.getId();
            this.documentProperties = blobTextStream.getXPaths();
            this.properties = blobTextStream.getProperties();
            BlobMeta blobMeta = blobTextStream.getBlob();
            if (blobMeta != null) {
                this.blobDigest = blobMeta.getDigest();
            }
        }

        public Builder withDocumentProperties(Set<String> targetDocumentProperty) {
            this.documentProperties = targetDocumentProperty;
            return this;
        }

        public Builder withCustomProperties(Map<String, String> properties) {
            this.properties = properties;
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
                    || StringUtils.isBlank(documentRef)
                    || StringUtils.isBlank(repositoryName)
                    || created == null) {
                throw new IllegalArgumentException("Invalid Enrichment metadata has been given. " + this.toString());
            }

            if (labels == null) {
                labels = emptyList();
            }
            if (documentProperties == null) {
                documentProperties = emptySet();
            }
            if (properties == null) {
                properties = emptyMap();
            }
            if (context == null) {
                context = new Context(repositoryName, documentRef, blobDigest, documentProperties, properties);
            }

            return new EnrichmentMetadata(this);
        }
    }
}
