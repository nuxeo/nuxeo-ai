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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.pipes.types.BlobTextStream;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.ecm.platform.picture.api.ImagingConvertConstants;
import org.nuxeo.runtime.api.Framework;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Helper methods for enrichment services
 */
public class EnrichmentUtils {

    public static final int DEFAULT_IMAGE_WIDTH = 299;

    public static final int DEFAULT_IMAGE_HEIGHT = 299;

    public static final int DEFAULT_IMAGE_DEPTH = 16;

    public static final String DEFAULT_CONVERSATION_FORMAT = "jpg";

    public static final String PICTURE_RESIZE_CONVERTER = "pictureResize";

    private static final Log log = LogFactory.getLog(EnrichmentUtils.class);

    // Static Utility class
    private EnrichmentUtils() {
    }

    /**
     * Gets the names of all properties used by the BlobTextStream.
     */
    public static Set<String> getPropertyNames(BlobTextStream blobTextStream) {
        Set<String> inputProperties = new HashSet<>(blobTextStream.getBlobs().keySet());
        inputProperties.addAll(blobTextStream.getProperties().keySet());
        return inputProperties;
    }

    /**
     * Gets the digests of any blobs used by the BlobTextStream.
     */
    public static Set<String> getDigests(BlobTextStream bts) {
      return bts.getBlobs().values().stream().map(Blob::getDigest).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * Saves the blob using the using the specified transient store and returns the blob key.
     * If the transientStoreName is NULL the TransientStoreService will still return the default TransientStore
     */
    public static String saveRawBlob(Blob rawBlob, String transientStoreName) {
        TransientStore transientStore = Framework.getService(TransientStoreService.class).getStore(transientStoreName);
        String blobKey = UUID.randomUUID().toString();
        transientStore.putBlobs(blobKey, Collections.singletonList(rawBlob));
        return blobKey;
    }

    /**
     * Looks up the blob provider for the ManagedBlob and retrieves the blob using its key.
     */
    public static Blob getBlobFromProvider(ManagedBlob managedBlob) {
        BlobProvider provider = Framework.getService(BlobManager.class).getBlobProvider(managedBlob.getProviderId());
        return getBlobFromProvider(provider, managedBlob.getKey());
    }

    /**
     * Looks up the blob by key
     */
    public static Blob getBlobFromProvider(BlobProvider provider, String key) {
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.key = key;
        try {
            return provider.readBlob(blobInfo);
        } catch (IOException e) {
            log.error(String.format("Failed to read blob %s", key), e);
        }
        return null;
    }

    /**
     * Convert the provided image blob.
     */
    public static Blob convertImageBlob(Blob blob, int width, int height, int depth, String conversionFormat) {
        SimpleBlobHolder bh = new SimpleBlobHolder(blob);
        Map<String, Serializable> parameters = new HashMap<>();
        parameters.put(ImagingConvertConstants.OPTION_RESIZE_WIDTH, width);
        parameters.put(ImagingConvertConstants.OPTION_RESIZE_HEIGHT, height);
        parameters.put(ImagingConvertConstants.OPTION_RESIZE_DEPTH, depth);
        parameters.put(ImagingConvertConstants.CONVERSION_FORMAT, conversionFormat);
        return Framework.getService(ConversionService.class).convert(PICTURE_RESIZE_CONVERTER, bh, parameters)
                        .getBlob();
    }

    /**
     * Get an option as an integer
     */
    public static int optionAsInteger(Map<String, String> options, String option, int defaultValue) {
        String value = options.get(option);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

}
