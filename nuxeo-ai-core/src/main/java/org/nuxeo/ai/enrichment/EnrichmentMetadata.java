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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.AbstractMetaDataBuilder;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ai.metadata.TagSuggestion;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_INPUT_DOCPROP_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_MODEL;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_RAW_KEY_PROPERTY;
import static org.nuxeo.ai.AIConstants.NORMALIZED_PROPERTY;
import static org.nuxeo.ai.AIConstants.SUGGESTION_CONFIDENCE;
import static org.nuxeo.ai.AIConstants.SUGGESTION_LABEL;
import static org.nuxeo.ai.AIConstants.SUGGESTION_LABELS;
import static org.nuxeo.ai.AIConstants.SUGGESTION_PROPERTY;
import static org.nuxeo.ai.AIConstants.SUGGESTION_SUGGESTIONS;
import static org.nuxeo.ai.AIConstants.SUGGESTION_TIMESTAMP;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.getDigests;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.getPropertyNames;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

/**
 * A normalized view of the result of an Enrichment Service. This class is designed to be serialized as JSON.
 */
@JsonDeserialize(builder = EnrichmentMetadata.Builder.class)
public class EnrichmentMetadata extends AIMetadata implements Cloneable {

    private static final long serialVersionUID = -8838535848960975096L;

    private static final Logger log = LogManager.getLogger(EnrichmentMetadata.class);

    private final List<LabelSuggestion> labelSuggestions;

    private final List<TagSuggestion> tagSuggestions;

    private EnrichmentMetadata(Builder builder) {
        super(builder.modelName, builder.kind, builder.getContext(), builder.getCreator(), builder.created,
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
        return Objects.equals(labelSuggestions, metadata.labelSuggestions) && Objects.equals(tagSuggestions,
                metadata.tagSuggestions);
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
                                        .append("context", context)
                                        .append("kind", kind)
                                        .append("rawKey", rawKey)
                                        .toString();
    }

    @Override
    public Object clone() {
        try {
            return (EnrichmentMetadata) super.clone();
        } catch (CloneNotSupportedException e) {
            Context ctx = this.getContext();
            return new EnrichmentMetadata(
                    new Builder(this.kind, this.modelName, ctx.inputProperties, ctx.repositoryName, ctx.documentRef,
                            ctx.digests));
        }
    }

    /**
     * Create a enrichment Map using the enrichment metadata
     */
    public Map<String, Object> toMap() {
        List<Map<String, Object>> suggestions = new ArrayList<>(getLabels().size());
        getLabels().forEach(suggestion -> {
            Map<String, Object> anEntry = new HashMap<>();
            anEntry.put(SUGGESTION_PROPERTY, suggestion.getProperty());
            List<Map<String, Object>> values = new ArrayList<>(suggestion.getValues().size());
            suggestion.getValues().forEach(value -> {
                Map<String, Object> val = new HashMap<>(2);
                val.put(SUGGESTION_LABEL, value.getName());
                val.put(SUGGESTION_CONFIDENCE, value.getConfidence());
                val.put(SUGGESTION_TIMESTAMP, value.getTimestamp());
                values.add(val);
            });
            anEntry.put(SUGGESTION_LABELS, values);
            suggestions.add(anEntry);
        });

        Map<String, Object> anEntry = new HashMap<>();
        AIComponent aiComponent = Framework.getService(AIComponent.class);

        if (!suggestions.isEmpty()) {
            anEntry.put(SUGGESTION_SUGGESTIONS, suggestions);
        }

        try {
            if (StringUtils.isNotEmpty(getRawKey())) {
                TransientStore transientStore = aiComponent.getTransientStoreForEnrichmentProvider(getModelName());

                List<Blob> rawBlobs = transientStore.getBlobs(getRawKey());
                if (rawBlobs != null && rawBlobs.size() == 1) {
                    anEntry.put(ENRICHMENT_RAW_KEY_PROPERTY, rawBlobs.get(0));
                } else {
                    log.warn("Unexpected transient store raw blob information for {}. "
                            + "A single raw blob is expected.", modelName);
                }
            }

            EnrichmentMetadata clone = (EnrichmentMetadata) clone();
            clone.getLabels().forEach(LabelSuggestion::keepUniqueOnly);
            Blob metaDataBlob = Blobs.createJSONBlob(MAPPER.writeValueAsString(clone));
            anEntry.put(NORMALIZED_PROPERTY, metaDataBlob);
        } catch (IOException e) {
            throw new NuxeoException("Unable to process metadata blob", e);
        }

        anEntry.put(ENRICHMENT_MODEL, modelName);
        anEntry.put(ENRICHMENT_INPUT_DOCPROP_PROPERTY, context.inputProperties);
        if (log.isDebugEnabled()) {
            log.debug(String.format("Enriching doc %s with %s", context.documentRef, suggestions));
        }
        return anEntry;
    }

    public static class Builder extends AbstractMetaDataBuilder {

        private List<LabelSuggestion> labelSuggestions;

        private List<TagSuggestion> tagSuggestions;

        public Builder(String kind, String modelName, Set<String> inputProperties, String repositoryName,
                String documentRef, Set<String> digests) {
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
