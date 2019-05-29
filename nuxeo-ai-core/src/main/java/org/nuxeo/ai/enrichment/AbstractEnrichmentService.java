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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.nuxeo.ai.metadata.Suggestion;
import org.nuxeo.ai.metadata.SuggestionMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.Blobs;

/**
 * Basic implementation of an enrichment service with mimetype and max file size support.
 * <p>
 * It is the responsibility of the implementing class to save any raw data, however,
 * helper methods are provided by this class.  You can specify your own <code>TransientStore</code> name on the
 * descriptor using <code>transientStore</code>.
 */
public abstract class AbstractEnrichmentService implements EnrichmentService, EnrichmentSupport {

    public static final String MAX_RESULTS = "maxResults";

    public static final String SUGGESTION_PROPERTY = "suggestionProperty";

    protected String name;

    protected long maxSize;

    protected String kind;

    protected String transientStoreName;

    protected String suggestionProperty;

    protected Set<String> supportedMimeTypes = new HashSet<>();

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        this.name = descriptor.name;
        this.maxSize = descriptor.maxSize;
        this.kind = descriptor.kind;
        this.transientStoreName = descriptor.transientStoreName;
        this.suggestionProperty = descriptor.options.getOrDefault(SUGGESTION_PROPERTY, "UNSET_" + name);
    }

    @Override
    public void addMimeTypes(Collection<String> mimeTypes) {
        supportedMimeTypes.addAll(mimeTypes);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getKind() {
        return kind;
    }

    @Override
    public boolean supportsMimeType(String mimeType) {
        return supportedMimeTypes.isEmpty() || supportedMimeTypes.contains(mimeType);
    }

    @Override
    public boolean supportsSize(long size) {
        return size <= maxSize;
    }

    /**
     * Save the rawJson String as a blob using the configured TransientStore for this service and returns the blob key.
     */
    public String saveJsonAsRawBlob(String rawJson) {
        return EnrichmentUtils.saveRawBlob(Blobs.createJSONBlob(rawJson), transientStoreName);
    }

    @Override
    public Collection<SuggestionMetadata> suggest(BlobTextFromDocument blobtext) {
        Collection<EnrichmentMetadata> enrichment = enrich(blobtext);
        return enrichment.stream().map(m -> asSuggestion(blobtext, m)).collect(Collectors.toList());
    }

    protected SuggestionMetadata asSuggestion(BlobTextFromDocument blobtext, EnrichmentMetadata aiMetadata) {
        SuggestionMetadata.Builder builder = new SuggestionMetadata.Builder(Instant.now(), getKind(), getName(), blobtext);
        builder.withRawKey(aiMetadata.getRawKey());
        List<Suggestion> suggestions = new ArrayList<>();
        suggestions.add(new Suggestion(suggestionProperty, aiMetadata.getLabels()));
        builder.withSuggestions(suggestions);
        return builder.build();
    }
}
