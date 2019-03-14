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

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.pipes.services.JacksonUtil;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
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
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Helper methods for enrichment services
 */
public class EnrichmentUtils {

    public static final int DEFAULT_IMAGE_WIDTH = 299;

    public static final int DEFAULT_IMAGE_HEIGHT = 299;

    public static final int DEFAULT_IMAGE_DEPTH = 16;

    public static final String DEFAULT_CONVERSATION_FORMAT = "jpg";

    public static final String CONVERSION_SERVICE = "conversionService";

    public static final String DEFAULT_CONVERTER = "pictureResize";

    public static final String ENRICHMENT_CACHE_KV = "ENRICHMENT_CACHE_KEY_VALUE";

    protected static final TypeReference<List<EnrichmentMetadata>> ENRICHMENT_LIST_TYPE =
            new TypeReference<List<EnrichmentMetadata>>() {
            };

    private static final Log log = LogFactory.getLog(EnrichmentUtils.class);

    // Static Utility class
    private EnrichmentUtils() {
    }

    /**
     * Gets the names of all properties used by the BlobTextFromDocument.
     */
    public static Set<String> getPropertyNames(BlobTextFromDocument blobTextFromDoc) {
        Set<String> inputProperties = new HashSet<>(blobTextFromDoc.getBlobs().keySet());
        inputProperties.addAll(blobTextFromDoc.getProperties().keySet());
        return inputProperties;
    }

    /**
     * Gets the digests of any blobs used by the BlobTextFromDocument.
     */
    public static Set<String> getDigests(BlobTextFromDocument blobtext) {
        return blobtext.getBlobs().values().stream().map(Blob::getDigest).filter(Objects::nonNull)
                       .collect(Collectors.toSet());
    }

    /**
     * Creates a key based on a concatenation of the digests of any blobs used by the BlobTextFromDocument.
     */
    public static String makeKeyUsingBlobDigests(BlobTextFromDocument blobTextFromDoc, String prefix) {
        return makeKeyUsingStream(getDigests(blobTextFromDoc).stream(), prefix);
    }

    /**
     * Creates a key based on a concatenation of the property value hash codes used by the BlobTextFromDocument.
     */
    public static String makeKeyUsingProperties(BlobTextFromDocument blobTextFromDoc, String prefix) {
        return makeKeyUsingStream(blobTextFromDoc.getProperties().values()
                                                 .stream()
                                                 .map(s -> String.valueOf(s.hashCode())), prefix);
    }

    /**
     * Creates a key based on a concatenation of stream of strings.
     */
    public static String makeKeyUsingStream(Stream<String> stream, String prefix) {
        String key = stream.collect(Collectors.joining("_", prefix, ""));
        if (!prefix.equals(key)) {
            return key;
        } else {
            return null;
        }
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
     * Returns the raw String from the transient store for the provided metadata.
     */
    public static String getRawBlob(EnrichmentMetadata metadata) throws IOException {
        try {
            if (isNotBlank(metadata.getRawKey())) {
                TransientStore transientStore = Framework.getService(AIComponent.class)
                                                         .getTransientStoreForEnrichmentService(metadata.getServiceName());
                List<Blob> rawBlobs = transientStore.getBlobs(metadata.getRawKey());
                if (rawBlobs != null && rawBlobs.size() == 1) {
                    return rawBlobs.get(0).getString();
                }
            }
        } catch (IllegalArgumentException e) {
            log.debug("Unknown transient store. ", e);
        }
        if (log.isDebugEnabled()) {
            log.debug("Unknown raw blob for raw key " + metadata.getRawKey());
        }
        return "";
    }

    /**
     * Looks up the blob provider for the ManagedBlob and retrieves the blob using its key.
     */
    public static Blob getBlobFromProvider(ManagedBlob managedBlob) {
        BlobProvider provider = Framework.getService(BlobManager.class).getBlobProvider(managedBlob.getProviderId());
        return getBlobFromProvider(provider, managedBlob.getKey(), managedBlob.getLength(), managedBlob.getMimeType());
    }

    /**
     * Looks up the blob by key
     */
    public static Blob getBlobFromProvider(BlobProvider provider, String key, Long length, String mimeType) {
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.key = key;
        blobInfo.length = length;
        blobInfo.mimeType = mimeType;
        try {
            Blob gotBlob = provider.readBlob(blobInfo);
            if (gotBlob != null && gotBlob.getLength() > 0) {
                return gotBlob;
            } else {
                log.warn(String.format("Missing file for %s %s", provider.getClass().getSimpleName(), key));
            }
        } catch (IOException e) {
            log.error(String.format("Failed to read blob %s", key), e);
        }
        return null;
    }

    /**
     * Convert the provided image blob.
     */
    public static Blob convertImageBlob(String service, Blob blob, int width, int height, int depth, String conversionFormat) {
        SimpleBlobHolder bh = new SimpleBlobHolder(blob);
        Map<String, Serializable> parameters = new HashMap<>();
        parameters.put(ImagingConvertConstants.OPTION_RESIZE_WIDTH, width);
        parameters.put(ImagingConvertConstants.OPTION_RESIZE_HEIGHT, height);
        parameters.put(ImagingConvertConstants.OPTION_RESIZE_DEPTH, depth);
        parameters.put(ImagingConvertConstants.CONVERSION_FORMAT, conversionFormat);
        try {
            return Framework.getService(ConversionService.class).convert(service, bh, parameters).getBlob();
        } catch (NuxeoException | NullPointerException exception) {
            log.warn("Unable to convert image blob.", exception);
            return null;
        }
    }

    /**
     * Get an entry from the enrichment cache
     */
    public static Collection<EnrichmentMetadata> cacheGet(String cacheKey) {
        if (isNotBlank(cacheKey)) {
            KeyValueStore kvStore = Framework.getService(KeyValueService.class).getKeyValueStore(ENRICHMENT_CACHE_KV);
            byte[] result = kvStore.get(cacheKey);
            if (result != null) {
                try {
                    return JacksonUtil.MAPPER.readValue(result, ENRICHMENT_LIST_TYPE);
                } catch (IOException e) {
                    log.warn(String.format("Failed to read metadata from cache for key %s", cacheKey), e);
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * Put an entry in the enrichment cache, specify the TTL in seconds.
     */
    public static void cachePut(String cacheKey, Collection<EnrichmentMetadata> metadata, long ttl) {
        if (isNotBlank(cacheKey)) {
            KeyValueStore kvStore = Framework.getService(KeyValueService.class).getKeyValueStore(ENRICHMENT_CACHE_KV);
            try {
                byte[] result = JacksonUtil.MAPPER.writeValueAsBytes(metadata);
                if (result != null) {
                    kvStore.put(cacheKey, result, ttl);
                }
            } catch (JsonProcessingException e) {
                log.warn(String.format("Failed to serialize metadata to cache for key %s", cacheKey), e);
            }
        }
    }

    /**
     * Copy all the supplied metadata but using the BlobTextFromDocument as the context.
     */
    public static Collection<EnrichmentMetadata> copyEnrichmentMetadata(Collection<EnrichmentMetadata> metadata,
                                                                        BlobTextFromDocument blobTextFromDoc) {
        return metadata
                .stream()
                .<EnrichmentMetadata>map(meta ->
                                                 new EnrichmentMetadata.Builder(meta.created, meta.kind,
                                                                                meta.serviceName, blobTextFromDoc)
                                                         .withLabels(meta.getLabels())
                                                         .withTags(meta.getTags())
                                                         .withSuggestions(meta.getSuggestions())
                                                         .withRawKey(meta.rawKey)
                                                         .withDocumentProperties(meta.context.inputProperties)
                                                         .withCreator(meta.creator).build())
                .collect(Collectors.toList());
    }

    /**
     * Get an option as an integer
     */
    public static int optionAsInteger(Map<String, String> options, String option, int defaultValue) {
        String value = options.get(option);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

}
