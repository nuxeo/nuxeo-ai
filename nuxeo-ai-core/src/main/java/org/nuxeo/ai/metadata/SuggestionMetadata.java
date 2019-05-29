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
package org.nuxeo.ai.metadata;

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
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * A normalized view of the suggestion data. This class is designed to be serialized as JSON.
 *
 * SuggestionMetadata should be considered "internal" data and should not be used directly.
 */
@JsonDeserialize(builder = SuggestionMetadata.Builder.class)
public class SuggestionMetadata extends AIMetadata {

    private final List<Suggestion> suggestions;

    private SuggestionMetadata(Builder builder) {
        super(builder.serviceName, builder.kind, builder.context, builder.creator, builder.created, builder.rawKey);
        suggestions = unmodifiableList(builder.suggestions);
    }

    public List<Suggestion> getSuggestions() {
        return suggestions;
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
        SuggestionMetadata that = (SuggestionMetadata) o;
        return Objects.equals(suggestions, that.suggestions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), suggestions);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("created", created)
                                        .append("creator", creator)
                                        .append("serviceName", serviceName)
                                        .append("context", context)
                                        .append("kind", kind)
                                        .append("rawKey", rawKey)
                                        .append("suggestions", suggestions)
                                        .toString();
    }

    public static class Builder extends AbstractMetaDataBuilder {

        private List<Suggestion> suggestions;

        public Builder(String kind, String serviceName, Set<String> inputProperties, String repositoryName,
                String documentRef, Set<String> digests) {
            super(Instant.now(), kind, serviceName, repositoryName, documentRef, digests, inputProperties);
            suggestions = new ArrayList<>();
        }

        public Builder(Instant created, String kind, String serviceName, BlobTextFromDocument blobTextFromDoc) {
            super(created, kind, serviceName, blobTextFromDoc.getRepositoryName(), blobTextFromDoc.getId(),
                    getDigests(blobTextFromDoc), getPropertyNames(blobTextFromDoc));
            suggestions = new ArrayList<>();
        }

        @JsonCreator
        public Builder(@JsonProperty("created") Instant created,
                       @JsonProperty("kind") String kind,
                       @JsonProperty("serviceName") String serviceName,
                       @JsonProperty("context") AIMetadata.Context context) {
            super(created, kind, serviceName, context);
        }

        public SuggestionMetadata.Builder withSuggestions(List<Suggestion> suggestions) {
            this.suggestions = suggestions;
            return this;
        }

        @Override
        protected void buildContext() {
            if (context == null) {
                context = new AIMetadata.Context(repositoryName, documentRef, digests, inputProperties);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        protected SuggestionMetadata build(AbstractMetaDataBuilder abstractMetaDataBuilder) {

            if (suggestions == null) {
                suggestions = emptyList();
            }
            return new SuggestionMetadata(this);
        }
    }
}
