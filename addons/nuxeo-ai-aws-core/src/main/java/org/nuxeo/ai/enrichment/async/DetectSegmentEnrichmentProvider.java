/*
 * (C) Copyright 2022 Nuxeo (http://nuxeo.com/) and others.
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
 *     Michael Vachette
 */
package org.nuxeo.ai.enrichment.async;

import static java.util.Collections.singleton;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;
import static org.nuxeo.ai.enrichment.LabelsEnrichmentProvider.MINIMUM_CONFIDENCE;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueStore;

import software.amazon.awssdk.services.rekognition.model.GetSegmentDetectionRequest;
import software.amazon.awssdk.services.rekognition.model.GetSegmentDetectionResponse;
import software.amazon.awssdk.services.rekognition.model.SegmentDetection;
import software.amazon.awssdk.services.rekognition.model.SegmentType;

/**
 * Detects segments in Video
 */
public class DetectSegmentEnrichmentProvider extends AbstractEnrichmentProvider implements EnrichmentCachable {

    public static final String ASYNC_ACTION_NAME = "StartSegmentDetection";

    public static final String ENRICHMENT_NAME = "aws.videoSegmentDetection";

    public static final String SEGMENT_TYPE_OPTION = "segmentTypes";

    public static final String DEFAULT_CONFIDENCE = "70";

    public static final String DEFAULT_SEGMENT_TYPE = "SHOT,TECHNICAL_CUE";

    protected float minConfidence;

    protected SegmentType[] segmentTypes;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);

        String segmentTypesStr = descriptor.options.getOrDefault(SEGMENT_TYPE_OPTION, DEFAULT_SEGMENT_TYPE);
        String[] segmentTypesStrArray = segmentTypesStr.split(",");
        segmentTypes = Arrays.stream(segmentTypesStrArray).map(SegmentType::valueOf).toArray(SegmentType[]::new);

        minConfidence = Float.parseFloat(descriptor.options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument doc) {
        RekognitionService rs = Framework.getService(RekognitionService.class);
        KeyValueStore store = getStore();
        for (Map.Entry<String, ManagedBlob> blob : doc.getBlobs().entrySet()) {
            // Call startVideoSegmentDetection for each segment type
            for (SegmentType segmentType : segmentTypes) {
                String jobId = rs.startVideoSegmentDetection(blob.getValue(), segmentType);
                HashMap<String, Serializable> params = new HashMap<>();
                params.put("doc", doc);
                params.put("key", blob.getKey());
                params.put("segmentType", segmentType);
                storeCallback(store, jobId, params);
            }
        }

        return Collections.emptyList();
    }

    /**
     * Processes the result of the call to AWS.
     */
    public Collection<EnrichmentMetadata> processResult(BlobTextFromDocument blobTextFromDoc, String propName,
            String jobId) {

        RekognitionService rs = Framework.getService(RekognitionService.class);
        List<AIMetadata.Label> labels = new ArrayList<>();
        List<SegmentDetection> nativeSegmentObjects = new ArrayList<>();
        GetSegmentDetectionResponse result = null;
        do {
            GetSegmentDetectionRequest.Builder requestBuilder = GetSegmentDetectionRequest.builder().jobId(jobId);

            if (result != null && result.nextToken() != null) {
                requestBuilder.nextToken(result.nextToken());
            }
            result = rs.getClient().getSegmentDetection(requestBuilder.build());

            List<AIMetadata.Label> currentPageTags = result.segments()
                                                           .stream()
                                                           .map(c -> newLabel(c))
                                                           .filter(Objects::nonNull)
                                                           .collect(Collectors.toList());

            labels.addAll(currentPageTags);
            nativeSegmentObjects.addAll(result.segments());
        } while (result.nextToken() != null);

        List<EnrichmentMetadata> metadata = new ArrayList<>();
        String raw = toJsonString(jg -> {
            jg.writeObjectField("segmentDetails", nativeSegmentObjects);
        });
        String rawKey = saveJsonAsRawBlob(raw);

        metadata.add(
                new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc).withLabels(asLabels(labels))
                                                                           .withRawKey(rawKey)
                                                                           .withDocumentProperties(singleton(propName))
                                                                           .build());
        return metadata;
    }

    protected EnrichmentMetadata.Label newLabel(SegmentDetection segmentDetection) {
        if (SegmentType.SHOT.toString().equals(segmentDetection.type().toString())) {
            return new EnrichmentMetadata.Label(segmentDetection.type().toString(),
                    segmentDetection.shotSegment().confidence() / 100, segmentDetection.startTimestampMillis());
        } else if (SegmentType.TECHNICAL_CUE.toString().equals(segmentDetection.type().toString())) {
            return new EnrichmentMetadata.Label(segmentDetection.technicalCueSegment().type().toString(),
                    segmentDetection.technicalCueSegment().confidence() / 100, segmentDetection.startTimestampMillis());
        } else {
            throw new NuxeoException("Unknown video segment type: " + segmentDetection.type());
        }
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }

}
