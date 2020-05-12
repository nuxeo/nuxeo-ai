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
import static org.nuxeo.ai.enrichment.LabelsEnrichmentProvider.MINIMUM_CONFIDENCE;
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
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.pipes.types.PropertyNameType;
import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueStore;

import com.amazonaws.services.rekognition.model.BoundingBox;
import com.amazonaws.services.rekognition.model.CelebrityDetail;
import com.amazonaws.services.rekognition.model.CelebrityRecognition;
import com.amazonaws.services.rekognition.model.GetCelebrityRecognitionResult;

/**
 * Detects celebrity faces in an image
 */
public class DetectCelebritiesEnrichmentProvider extends AbstractEnrichmentProvider implements EnrichmentCachable {

    public static final String ASYNC_ACTION_NAME = "StartCelebrityRecognition";

    public static final String ENRICHMENT_NAME = "aws.videoCelebrityDetection";

    public static final String DEFAULT_CONFIDENCE = "70";

    protected float minConfidence;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        minConfidence = Float.parseFloat(descriptor.options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument doc) {
        RekognitionService rs = Framework.getService(RekognitionService.class);
        KeyValueStore store = getStore();
        for (Map.Entry<PropertyNameType, ManagedBlob> blob : doc.getPropertyBlobs().entrySet()) {
            String jobId = rs.startDetectCelebrityFaces(blob.getValue());
            HashMap<String, Serializable> params = new HashMap<>();
            params.put("doc", doc);
            params.put("key", blob.getKey().getName());

            storeCallback(store, jobId, params);
        }

        return Collections.emptyList();
    }

    /**
     * Processes the result of the call to AWS
     */
    public Collection<EnrichmentMetadata> processResults(BlobTextFromDocument blobTextFromDoc, String propName,
                                                            GetCelebrityRecognitionResult result) {
        List<AIMetadata.Tag> tags = result.getCelebrities().stream()
                .map(c -> newCelebrityTag(c.getCelebrity(), c.getTimestamp()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        String raw = toJsonString(jg -> {
            jg.writeObjectField("celebrityFaces", result.getCelebrities().stream()
                    .map(CelebrityRecognition::getCelebrity));
            jg.writeObjectField("unrecognizedFaces", Collections.emptyList());
        });

        String rawKey = saveJsonAsRawBlob(raw);
        return Collections.singletonList(new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc)
                .withTags(asTags(tags))
                .withRawKey(rawKey)
                .withDocumentProperties(singleton(propName))
                .build());
    }

    /**
     * Create a AI Tag based on the celebrity face.
     */
    protected AIMetadata.Tag newCelebrityTag(CelebrityDetail celebrity, long timestamp) {
        BoundingBox box = celebrity.getFace().getBoundingBox();
        if (celebrity.getFace().getConfidence() >= minConfidence) {
            return new AIMetadata.Tag(celebrity.getName(), kind, celebrity.getId(),
                    new AIMetadata.Box(box.getWidth(), box.getHeight(), box.getLeft(), box.getTop()),
                    Collections.singletonList(new AIMetadata.Label(null, 0, timestamp)),
                    celebrity.getFace().getConfidence() / 100
            );
        }
        return null;
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }
}
