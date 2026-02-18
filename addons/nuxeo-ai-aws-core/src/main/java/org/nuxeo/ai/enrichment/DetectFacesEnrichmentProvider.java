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

import static java.util.Collections.singleton;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;
import static org.nuxeo.ai.enrichment.LabelsEnrichmentProvider.MINIMUM_CONFIDENCE;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;

import software.amazon.awssdk.services.rekognition.model.Attribute;
import software.amazon.awssdk.services.rekognition.model.DetectFacesResponse;
import software.amazon.awssdk.services.rekognition.model.FaceDetail;

/**
 * Detects faces in an image.
 */
public class DetectFacesEnrichmentProvider extends AbstractEnrichmentProvider implements EnrichmentCachable {

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
        return AWSHelper.handlingExceptions(() -> {
            List<EnrichmentMetadata> enriched = new ArrayList<>();
            for (Map.Entry<String, ManagedBlob> blob : doc.getBlobs().entrySet()) {
                DetectFacesResponse result = rs.detectFaces(blob.getValue());
                if (result != null && !result.faceDetails().isEmpty()) {
                    enriched.addAll(processResults(doc, blob.getKey(), result));
                }
            }
            return enriched;
        });
    }

    /**
     * Processes the result of the call to AWS.
     */
    protected Collection<EnrichmentMetadata> processResults(BlobTextFromDocument blobTextFromDoc, String propName,
            DetectFacesResponse result) {
        List<EnrichmentMetadata> metadata = new ArrayList<>();
        String raw = toJsonString(jg -> {
            jg.writeObjectField("faceDetails", result.faceDetails());
            if (result.orientationCorrection() != null) {
                jg.writeStringField("orientationCorrection", result.orientationCorrection().toString());
            }
        });
        String rawKey = saveJsonAsRawBlob(raw);

        List<AIMetadata.Tag> tags = result.faceDetails()
                                          .stream()
                                          .map(this::newFaceTag)
                                          .filter(Objects::nonNull)
                                          .collect(Collectors.toList());

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
    protected AIMetadata.Tag newFaceTag(FaceDetail faceDetail) {
        software.amazon.awssdk.services.rekognition.model.BoundingBox box = faceDetail.boundingBox();
        if (faceDetail.confidence() >= minConfidence) {
            List<AIMetadata.Label> labels = collectLabels(faceDetail);
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
            labels.add(new AIMetadata.Label(faceDetail.gender().valueAsString().toLowerCase(),
                    faceDetail.gender().confidence() / 100, timestamp));
        }

        if (faceDetail.emotions() != null && !faceDetail.emotions().isEmpty()) {
            faceDetail.emotions().forEach(emotion -> {
                if (emotion.confidence() > minConfidence) {
                    labels.add(new AIMetadata.Label(emotion.typeAsString().toLowerCase(), emotion.confidence() / 100,
                            timestamp));
                }
            });
        }

        return labels;
    }

    protected List<AIMetadata.Label> collectLabels(FaceDetail faceDetail) {
        return collectLabels(faceDetail, 0L);
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }

}
