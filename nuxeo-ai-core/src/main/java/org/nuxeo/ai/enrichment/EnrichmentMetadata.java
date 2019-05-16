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
import static org.nuxeo.ai.enrichment.EnrichmentUtils.getDigests;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.getPropertyNames;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.AbstractMetaDataBuilder;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * A normalized view of the result of an Enrichment Service. This class is designed to be serialized as JSON.
 */
@JsonDeserialize(builder = EnrichmentMetadata.Builder.class)
public class EnrichmentMetadata extends AIMetadata {

    private static final long serialVersionUID = -8838535848960975096L;

    private final List<Label> labels;

    private final List<Tag> tags;

    private EnrichmentMetadata(Builder builder) {
        super(builder.serviceName, builder.kind, builder.getContext(), builder.getCreator(), builder.created,
                builder.getRawKey());
        labels = unmodifiableList(builder.labels);
        tags = unmodifiableList(builder.tags);
    }

    public List<Tag> getTags() {
        return tags;
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
        return Objects.equals(labels, metadata.labels) && Objects.equals(tags, metadata.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), labels, tags);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("labels", labels)
                                        .append("tags", tags)
                                        .append("created", created)
                                        .append("creator", creator)
                                        .append("serviceName", serviceName)
                                        .append("context", context)
                                        .append("kind", kind)
                                        .append("rawKey", rawKey)
                                        .toString();
    }

    public static class Builder extends AbstractMetaDataBuilder {

        // optional
        private List<Label> labels;

        private List<Tag> tags;

        public Builder(Instant created, String kind, String serviceName, BlobTextFromDocument blobTextFromDoc) {
            super(created, kind, serviceName, blobTextFromDoc.getRepositoryName(), blobTextFromDoc.getId(),
                    getDigests(blobTextFromDoc), getPropertyNames(blobTextFromDoc));
            labels = new ArrayList<>();
            tags = new ArrayList<>();
        }

        public Builder(String kind, String serviceName, BlobTextFromDocument blobTextFromDoc) {
            this(Instant.now(), kind, serviceName, blobTextFromDoc);
        }

        @JsonCreator
        public Builder(@JsonProperty("created") Instant created,
                       @JsonProperty("kind") String kind,
                       @JsonProperty("serviceName") String serviceName,
                       @JsonProperty("context") AIMetadata.Context context) {
            super(created, kind, serviceName, context);
        }

        /**
         * Set the metadata labels overwriting any existing ones.
         */
        public Builder withLabels(List<Label> labels) {
            this.labels = labels;
            return this;
        }

        /**
         * Set the metadata tags overwriting any existing ones.
         */
        public Builder withTags(List<Tag> tags) {
            this.tags = tags;
            return this;
        }

        /**
         * Adds tags to the existing list of tags.
         */
        public void addTags(List<Tag> tags) {
            this.tags.addAll(tags);
        }

        /**
         * Adds tag to the existing list of tags.
         */
        public void addTag(Tag tag) {
            this.tags.add(tag);
        }

        /**
         * Adds labels to the existing list of labels.
         */
        public void addLabels(List<Label> labels) {
            this.labels.addAll(labels);
        }

        /**
         * Adds a label to the existing list of labels.
         */
        public void addLabel(Label label) {
            this.labels.add(label);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected EnrichmentMetadata build(AbstractMetaDataBuilder abstractMetaDataBuilder) {

            if (labels == null) {
                labels = emptyList();
            }
            if (tags == null) {
                tags = emptyList();
            }
            return new EnrichmentMetadata(this);
        }
    }
}
