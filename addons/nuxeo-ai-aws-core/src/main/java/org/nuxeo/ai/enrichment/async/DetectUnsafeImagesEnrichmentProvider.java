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
 *     anechaev
 */
package org.nuxeo.ai.enrichment.async;

import static java.util.Collections.singleton;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.nuxeo.ai.enrichment.AbstractEnrichmentProvider;
import org.nuxeo.ai.enrichment.EnrichmentCachable;
import org.nuxeo.ai.enrichment.EnrichmentDescriptor;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.pipes.types.PropertyType;
import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueStore;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.rekognition.model.GetContentModerationResult;
import com.amazonaws.services.rekognition.model.ModerationLabel;
import net.jodah.failsafe.RetryPolicy;

/**
 * Detect unsafe content in images.
 */
public class DetectUnsafeImagesEnrichmentProvider extends AbstractEnrichmentProvider implements EnrichmentCachable {

    public static final String ASYNC_ACTION_NAME = "StartContentModeration";

    public static final String ENRICHMENT_NAME = "aws.unsafeVideo";

    public static final String MINIMUM_CONFIDENCE = "minConfidence";

    public static final String DEFAULT_CONFIDENCE = "70";

    protected float minConfidence;

    protected EnrichmentMetadata.Label newLabel(ModerationLabel l, long timestamp) {
        if (l.getConfidence() >= minConfidence) {
            return new EnrichmentMetadata.Label(l.getName(), l.getConfidence() / 100, timestamp);
        } else {
            return null;
        }
    }

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        Map<String, String> options = descriptor.options;
        minConfidence = Float.parseFloat(options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument doc) {
        RekognitionService rs = Framework.getService(RekognitionService.class);
        KeyValueStore store = getStore();
        for (Map.Entry<String, ManagedBlob> blob : doc.getBlobs().entrySet()) {
            String jobId = rs.startDetectUnsafe(blob.getValue());
            HashMap<String, Serializable> params = new HashMap<>();
            params.put("doc", doc);
            params.put("key", blob.getKey());

            storeCallback(store, jobId, params);
        }

        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy().abortOn(SdkClientException.class);
    }

    public Collection<EnrichmentMetadata> processResult(BlobTextFromDocument blobTextFromDoc, String propName,
                                                        GetContentModerationResult result) {
        List<EnrichmentMetadata.Label> labels = result.getModerationLabels()
                .stream()
                .map(l -> newLabel(l.getModerationLabel(), l.getTimestamp()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        String raw = toJsonString(jg -> jg.writeObjectField("labels", result.getModerationLabels()));

        String rawKey = saveJsonAsRawBlob(raw);
        return Collections.singletonList(new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc)
                .withLabels(asLabels(labels))
                .withRawKey(rawKey)
                .withDocumentProperties(singleton(propName))
                .build());
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }
}
