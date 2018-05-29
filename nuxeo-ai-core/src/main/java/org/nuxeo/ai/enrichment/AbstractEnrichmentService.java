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

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.http.entity.ContentType;
import org.nuxeo.ai.AIComponent;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Basic implementation of an enrichment service with mimetype and max file size support.
 * <p>
 * It is the responsibility of the implementing class to save any raw data, however,
 * helper methods are provided by this class.  You can specify your own blob provider on the
 * descriptor using blobProviderId.
 */
public abstract class AbstractEnrichmentService implements EnrichmentService {

    public static final String MAX_RESULTS = "maxResults";

    protected String name;
    protected long maxSize;
    protected String blobProviderId;
    protected Set<String> supportedMimeTypes = new HashSet<>();

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        this.name = descriptor.name;
        this.maxSize = descriptor.maxSize;
        blobProviderId = AIComponent.getBlobProviderId(descriptor);
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
    public String getBlobProviderId() {
        return blobProviderId;
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
     * Saves the blob using the configured blob provider for this service and returns the blob key
     */
    public String saveRawBlob(Blob rawBlob, String repositoryName) {
        return TransactionHelper.runInTransaction(
                () -> CoreInstance.doPrivileged(repositoryName, session -> {
                    BlobManager blobManager = Framework.getService(BlobManager.class);
                    try {
                        return blobManager.getBlobProvider(blobProviderId).writeBlob(rawBlob);
                    } catch (IOException e) {
                        throw new NuxeoException("Unable to save the raw blob ", e);
                    }
                })
        );
    }

    /**
     * Save the rawJson String provided as a blob and returns the blob key
     */
    public String saveJsonAsRawBlob(String rawJson, String repositoryName) {
        Blob raw = new StringBlob(rawJson, ContentType.APPLICATION_JSON.getMimeType());
        return saveRawBlob(raw, repositoryName);
    }
}
