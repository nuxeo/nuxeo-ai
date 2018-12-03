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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A normalized view of the suggestion data.
 * This class is designed to be serialized as JSON.
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
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        if (!super.equals(o)) { return false; }
        SuggestionMetadata that = (SuggestionMetadata) o;
        return Objects.equals(suggestions, that.suggestions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), suggestions);
    }

    public static class Builder extends AbstractMetaDataBuilder {

        private List<Suggestion> suggestions;

        public Builder(String kind, String serviceName, Set<String> inputProperties) {
            super(Instant.now(), kind, serviceName, null, null, null, inputProperties);
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
