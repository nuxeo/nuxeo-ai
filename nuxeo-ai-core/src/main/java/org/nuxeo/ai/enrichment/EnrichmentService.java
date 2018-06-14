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
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobMeta;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;

import net.jodah.failsafe.RetryPolicy;

/**
 * Enriches something using a service (usually an external service)
 * AbstractEnrichmentService provides a general implementation.
 *
 * @see AbstractEnrichmentService
 */
public interface EnrichmentService {

    /**
     * Initialize the service based on the descriptor
     */
    void init(EnrichmentDescriptor descriptor);

    /**
     * Adds supported mimetypes
     */
    void addMimeTypes(Collection<String> mimeTypes);

    /**
     * The name of the service
     */
    String getName();

    /**
     * The kind of service. The kind must match an id of an entry in the "aikind" vocabulary.
     */
    String getKind();

    /**
     * Does the service support the mimeType.
     * If the service doesn't declare what it supports then this method will return true.
     */
    boolean supportsMimeType(String mimeType);

    /**
     * Does the service support a file of this size (in bytes).
     */
    boolean supportsSize(long size);

    /**
     * The main method for the service to implement.  Enriching the blob or text and returning a result.
     */
    Collection<EnrichmentMetadata> enrich(BlobTextStream blobTextStream);

    /**
     * The retry policy for the service
     */
    @SuppressWarnings("unchecked")
    default RetryPolicy getRetryPolicy() {
        return new RetryPolicy().abortOn(NuxeoException.class);
    }

    /**
     * Saves the blob using the using the specified transient store and returns the blob key
     */
    default String saveRawBlob(Blob rawBlob, String transientStoreName) {
        TransientStore transientStore = Framework.getService(TransientStoreService.class).getStore(transientStoreName);
        String blobKey = UUID.randomUUID().toString();
        transientStore.putBlobs(blobKey, Collections.singletonList(rawBlob));
        return blobKey;
    }

    /**
     * Read the blob as an input stream.
     *
     * @throws IOException
     */
    default InputStream readBlob(BlobMeta blobMeta) throws IOException {
        BlobProvider blobProvider = Framework.getService(BlobManager.class).getBlobProvider(blobMeta.getProviderId());
        if (blobProvider != null) {
            BlobInfo blobInfo = new BlobInfo();
            blobInfo.key = blobMeta.getKey();
            Blob blob = blobProvider.readBlob(blobInfo);
            if (blob != null) {
                return blob.getStream();
            }
        }
        throw new IOException("Unable to read blob: " + blobMeta);
    }
}
