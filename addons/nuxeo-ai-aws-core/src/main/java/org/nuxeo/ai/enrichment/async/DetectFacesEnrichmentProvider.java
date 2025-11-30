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
import java.util.ArrayList;
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
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueStore;

import software.amazon.awssdk.services.rekognition.model.Attribute;
import software.amazon.awssdk.services.rekognition.model.BoundingBox;
import software.amazon.awssdk.services.rekognition.model.FaceDetail;
import software.amazon.awssdk.services.rekognition.model.FaceDetection;
import software.amazon.awssdk.services.rekognition.model.GetFaceDetectionRequest;
import software.amazon.awssdk.services.rekognition.model.GetFaceDetectionResponse;

/**
 * Detects faces in an image.
 */
public class DetectFacesEnrichmentProvider extends AbstractEnrichmentProvider implements EnrichmentCachable {

    public static final String ASYNC_ACTION_NAME = "StartFaceDetection";

    public static final String ENRICHMENT_NAME = "aws.videoFaceDetection";

    public static final String ATTRIBUTES_OPTION = "attribute";

    public static final String DEFAULT_CONFIDENCE = "70";

    public static final String DEFAULT_ATTRIBUTES = "ALL";

    protected float minConfidence;

    protected Attribute attribute;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        attribute = Attribute.valueOf(descriptor.options.getOrDefault(ATTRIBUTES_OPTION, DEFAULT_ATTRIBUTES));
        minConfidence = Float.parseFloat(descriptor.options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument doc) {
        RekognitionService rs = Framework.getService(RekognitionService.class);
        KeyValueStore store = getStore();
        for (Map.Entry<String, ManagedBlob> blob : doc.getBlobs().entrySet()) {
            String jobId = rs.startDetectFaces(blob.getValue());
            HashMap<String, Serializable> params = new HashMap<>();
            params.put("doc", doc);
            params.put("key", blob.getKey());

            storeCallback(store, jobId, params);
        }

        return Collections.emptyList();
    }

    /**
     * Processes the result of the call to AWS.
     */
    public Collection<EnrichmentMetadata> processResults(BlobTextFromDocument blobTextFromDoc, String propName,
            String jobId) {

        RekognitionService rs = Framework.getService(RekognitionService.class);
        List<AIMetadata.Tag> tags = new ArrayList<>();
        List<FaceDetection> nativeFaceObjects = new ArrayList<>();
        GetFaceDetectionResponse result = null;
        do {
            GetFaceDetectionRequest.Builder requestBuilder = GetFaceDetectionRequest.builder().jobId(jobId);

            if (result != null && result.nextToken() != null) {
                requestBuilder.nextToken(result.nextToken());
            }
            result = rs.getClient().getFaceDetection(requestBuilder.build());

            List<AIMetadata.Tag> currentPageTags = result.faces()
                                                         .stream()
                                                         .map(c -> newFaceTag(c.face(), c.timestamp()))
                                                         .filter(Objects::nonNull)
                                                         .collect(Collectors.toList());

            tags.addAll(currentPageTags);
            nativeFaceObjects.addAll(result.faces());
        } while (result.nextToken() != null);

        List<EnrichmentMetadata> metadata = new ArrayList<>();
        String raw = toJsonString(jg -> {
            jg.writeObjectField("faceDetails", nativeFaceObjects);
        });
        String rawKey = saveJsonAsRawBlob(raw);

        metadata.add(
                new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc).withTags(asTags(tags))
                                                                           .withRawKey(rawKey)
                                                                           .withDocumentProperties(singleton(propName))
                                                                           .build());
        return metadata;
    }

    /**
     * Create a AI Tag based on the face details.
     */
    protected AIMetadata.Tag newFaceTag(FaceDetail faceDetail, long timestamp) {
        BoundingBox box = faceDetail.boundingBox();
        if (faceDetail.confidence() >= minConfidence) {
            List<AIMetadata.Label> labels = collectLabels(faceDetail, timestamp);
            return new AIMetadata.Tag("face", kind, null,
                    new AIMetadata.Box(box.width(), box.height(), box.left(), box.top()), labels,
                    faceDetail.confidence());
        }
        return null;
    }

    /**
     * Adds extra labels for this face.
     */
    protected List<AIMetadata.Label> collectLabels(FaceDetail faceDetail, long timestamp) {
        List<AIMetadata.Label> labels = new ArrayList<>();
        if (faceDetail.smile() != null && faceDetail.smile().value()
                && faceDetail.smile().confidence() > minConfidence) {
            labels.add(new AIMetadata.Label("smile", faceDetail.smile().confidence() / 100, timestamp));
        }

        if (faceDetail.eyeglasses() != null && faceDetail.eyeglasses().value()
                && faceDetail.eyeglasses().confidence() > minConfidence) {
            labels.add(new AIMetadata.Label("eyeglasses", faceDetail.eyeglasses().confidence() / 100, timestamp));
        }

        if (faceDetail.sunglasses() != null && faceDetail.sunglasses().value()
                && faceDetail.sunglasses().confidence() > minConfidence) {
            labels.add(new AIMetadata.Label("sunglasses", faceDetail.sunglasses().confidence() / 100, timestamp));
        }

        if (faceDetail.beard() != null && faceDetail.beard().value()
                && faceDetail.beard().confidence() > minConfidence) {
            labels.add(new AIMetadata.Label("beard", faceDetail.beard().confidence() / 100, timestamp));
        }

        if (faceDetail.mustache() != null && faceDetail.mustache().value()
                && faceDetail.mustache().confidence() > minConfidence) {
            labels.add(new AIMetadata.Label("mustache", faceDetail.mustache().confidence() / 100, timestamp));
        }

        if (faceDetail.eyesOpen() != null && faceDetail.eyesOpen().value()
                && faceDetail.eyesOpen().confidence() > minConfidence) {
            labels.add(new AIMetadata.Label("eyesOpen", faceDetail.eyesOpen().confidence() / 100, timestamp));
        }

        if (faceDetail.mouthOpen() != null && faceDetail.mouthOpen().value()
                && faceDetail.mouthOpen().confidence() > minConfidence) {
            labels.add(new AIMetadata.Label("mouthOpen", faceDetail.mouthOpen().confidence() / 100, timestamp));
        }

        if (faceDetail.gender() != null && faceDetail.gender().confidence() > minConfidence) {
            labels.add(new AIMetadata.Label(faceDetail.gender().value().toString().toLowerCase(),
                    faceDetail.gender().confidence() / 100, timestamp));
        }

        if (faceDetail.emotions() != null && !faceDetail.emotions().isEmpty()) {
            faceDetail.emotions().forEach(emotion -> {
                if (emotion.confidence() > minConfidence) {
                    labels.add(new AIMetadata.Label(emotion.type().toString().toLowerCase(), emotion.confidence() / 100,
                            timestamp));
                }
            });
        }

        return labels;
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }

}
