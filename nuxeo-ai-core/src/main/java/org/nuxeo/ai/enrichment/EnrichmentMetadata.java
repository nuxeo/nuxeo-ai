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
import java.util.Set;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.AbstractMetaDataBuilder;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ai.metadata.TagSuggestion;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * A normalized view of the result of an Enrichment Service. This class is designed to be serialized as JSON.
 */
@JsonDeserialize(builder = EnrichmentMetadata.Builder.class)
public class EnrichmentMetadata extends AIMetadata {

    private static final long serialVersionUID = -8838535848960975096L;

    private final List<LabelSuggestion> labelSuggestions;

    private final List<TagSuggestion> tagSuggestions;

    private EnrichmentMetadata(Builder builder) {
        super(builder.modelName, builder.getModelVersion(), builder.kind, builder.getContext(), builder.getCreator(), builder.created,
              builder.getRawKey());
        labelSuggestions = unmodifiableList(builder.labelSuggestions);
        tagSuggestions = unmodifiableList(builder.tagSuggestions);
    }

    public List<TagSuggestion> getTags() {
        return tagSuggestions;
    }

    public List<LabelSuggestion> getLabels() {
        return labelSuggestions;
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
        return Objects.equals(labelSuggestions, metadata.labelSuggestions)
                && Objects.equals(tagSuggestions, metadata.tagSuggestions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), labelSuggestions, tagSuggestions);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("labelSuggestions", labelSuggestions)
                                        .append("tagSuggestions", tagSuggestions)
                                        .append("created", created)
                                        .append("creator", creator)
                                        .append("modelName", modelName)
                                        .append("modelVersion", modelVersion)
                                        .append("context", context)
                                        .append("kind", kind)
                                        .append("rawKey", rawKey)
                                        .toString();
    }

    public static class Builder extends AbstractMetaDataBuilder {

        private List<LabelSuggestion> labelSuggestions;

        private List<TagSuggestion> tagSuggestions;

        public Builder(String kind, String modelName, Set<String> inputProperties,
                String repositoryName, String documentRef, Set<String> digests) {
            super(Instant.now(), kind, modelName, repositoryName, documentRef, digests, inputProperties);
            labelSuggestions = new ArrayList<>();
            tagSuggestions = new ArrayList<>();
        }

        public Builder(String kind, String modelName, BlobTextFromDocument blobTextFromDoc) {
            this(Instant.now(), kind, modelName, blobTextFromDoc);
        }

        public Builder(Instant created, String kind, String modelName, BlobTextFromDocument blobTextFromDoc) {
            super(created, kind, modelName, blobTextFromDoc.getRepositoryName(), blobTextFromDoc.getId(),
                    getDigests(blobTextFromDoc), getPropertyNames(blobTextFromDoc));
            labelSuggestions = new ArrayList<>();
            tagSuggestions = new ArrayList<>();
        }

        @JsonCreator
        public Builder(@JsonProperty("created") Instant created, @JsonProperty("kind") String kind,
                @JsonProperty("modelName") String modelName, @JsonProperty("context") AIMetadata.Context context) {
            super(created, kind, modelName, context);
        }

        public EnrichmentMetadata.Builder withLabels(List<LabelSuggestion> labelSuggestions) {
            this.labelSuggestions = labelSuggestions;
            return this;
        }

        public EnrichmentMetadata.Builder withTags(List<TagSuggestion> tagSuggestions) {
            this.tagSuggestions = tagSuggestions;
            return this;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected EnrichmentMetadata build(AbstractMetaDataBuilder abstractMetaDataBuilder) {

            if (labelSuggestions == null) {
                labelSuggestions = emptyList();
            }
            if (tagSuggestions == null) {
                tagSuggestions = emptyList();
            }
            return new EnrichmentMetadata(this);
        }
    }
}
