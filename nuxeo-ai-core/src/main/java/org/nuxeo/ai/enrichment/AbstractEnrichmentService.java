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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.runtime.api.Framework;

/**
 * Basic implementation of an enrichment service with mimetype and max file size support.
 * <p>
 * It is the responsibility of the implementing class to save any raw data, however,
 * helper methods are provided by this class.  You can specify your own <code>TransientStore</code> name on the
 * descriptor using <code>transientStore</code>.
 */
public abstract class AbstractEnrichmentService implements EnrichmentService {

    public static final String MAX_RESULTS = "maxResults";

    protected String name;
    protected long maxSize;
    protected String kind;
    protected String transientStoreName;
    protected Set<String> supportedMimeTypes = new HashSet<>();

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        this.name = descriptor.name;
        this.maxSize = descriptor.maxSize;
        this.kind = descriptor.kind;
        this.transientStoreName = descriptor.transientStoreName;
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
     * Saves the blob using the configured TransientStore for this service and returns the blob key
     */
    public String saveRawBlob(Blob rawBlob) {
        TransientStore transientStore = Framework.getService(TransientStoreService.class).getStore(transientStoreName);
        String blobKey = UUID.randomUUID().toString();
        transientStore.putBlobs(blobKey, Collections.singletonList(rawBlob));
        return blobKey;
    }

    /**
     * Save the rawJson String provided as a blob and returns the blob key
     */
    public String saveJsonAsRawBlob(String rawJson) {
        return saveRawBlob(Blobs.createJSONBlob(rawJson));
    }
}
