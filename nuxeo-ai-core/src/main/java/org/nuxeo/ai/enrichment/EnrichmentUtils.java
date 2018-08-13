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

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.runtime.api.Framework;

/**
 * Helper methods for enrichment services
 */
public class EnrichmentUtils {

    public static final String HEIGHT = "height";

    public static final String WIDTH = "width";

    public static final String DEPTH = "depth";

    protected static final String PICTURE_RESIZE_CONVERTER = "pictureResize";

    /**
     * Saves the blob using the using the specified transient store and returns the blob key
     */
    public String saveRawBlob(Blob rawBlob, String transientStoreName) {
        TransientStore transientStore = Framework.getService(TransientStoreService.class).getStore(transientStoreName);
        String blobKey = UUID.randomUUID().toString();
        transientStore.putBlobs(blobKey, Collections.singletonList(rawBlob));
        return blobKey;
    }

    /**
     * Convert the provided image blob.
     */
    public Blob convertImageBlob(Blob blob, int width, int height, int depth) {
        SimpleBlobHolder bh = new SimpleBlobHolder(blob);
        Map<String, Serializable> parameters = new HashMap<>();
        parameters.put(WIDTH, width);
        parameters.put(HEIGHT, height);
        parameters.put(DEPTH, depth);
        return Framework.getService(ConversionService.class).convert(PICTURE_RESIZE_CONVERTER, bh, parameters).getBlob();
    }
}
