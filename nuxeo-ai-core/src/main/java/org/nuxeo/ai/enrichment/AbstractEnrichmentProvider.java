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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ai.metadata.TagSuggestion;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;
import org.nuxeo.runtime.services.config.ConfigurationService;

/**
 * Basic implementation of an enrichment provider with mimetype and max file size support.
 * <p>
 * It is the responsibility of the implementing class to save any raw data, however, helper methods are provided by this
 * class. You can specify your own <code>TransientStore</code> name on the descriptor using <code>transientStore</code>.
 */
public abstract class AbstractEnrichmentProvider implements EnrichmentProvider, EnrichmentSupport {

    public static final String CALLBACK_KVS_TTL = "nuxeo.enrichment.aws.video.kvs.ttl.ms";

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
        this.suggestionProperty = descriptor.options.getOrDefault(SUGGESTION_PROPERTY, UNSET + name);
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
     * Save the rawJson String as a blob using the configured TransientStore for this provider and returns the blob key.
     */
    public String saveJsonAsRawBlob(String rawJson) {
        return EnrichmentUtils.saveRawBlob(Blobs.createJSONBlob(rawJson), transientStoreName);
    }

    protected List<LabelSuggestion> asLabels(List<AIMetadata.Label> labels) {
        return Collections.singletonList(new LabelSuggestion(suggestionProperty, labels));
    }

    protected List<TagSuggestion> asTags(List<AIMetadata.Tag> tags) {
        return Collections.singletonList(new TagSuggestion(suggestionProperty, tags));
    }

    protected KeyValueStore getStore() {
        KeyValueService kvs = Framework.getService(KeyValueService.class);
        return kvs.getKeyValueStore(transientStoreName);
    }

    protected void storeCallback(KeyValueStore store, String jobId, Map<String, Serializable> params) {
        ConfigurationService cs = Framework.getService(ConfigurationService.class);
        long duration = cs.getLong(CALLBACK_KVS_TTL, TimeUnit.DAYS.toMillis(1));
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(params);
            store.put(jobId, baos.toByteArray(), duration);
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }
}
