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

import com.amazonaws.services.rekognition.model.Attribute;
import com.amazonaws.services.rekognition.model.BoundingBox;
import com.amazonaws.services.rekognition.model.DetectFacesResult;
import com.amazonaws.services.rekognition.model.FaceDetail;

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
                DetectFacesResult result = rs.detectFaces(blob.getValue(), attribute);
                if (result != null && !result.getFaceDetails().isEmpty()) {
                    enriched.addAll(processResults(doc, blob.getKey(), result));
                }
            }
            return enriched;
        });
    }

    /**
     * Processes the result of the call to AWS.
     */
    protected Collection<EnrichmentMetadata> processResults(BlobTextFromDocument blobTextFromDoc,
                                                            String propName, DetectFacesResult result) {
        List<EnrichmentMetadata> metadata = new ArrayList<>();
        String raw = toJsonString(jg -> {
            jg.writeObjectField("faceDetails", result.getFaceDetails());
            jg.writeStringField("orientationCorrection", result.getOrientationCorrection());
        });
        String rawKey = saveJsonAsRawBlob(raw);

        List<AIMetadata.Tag> tags = result.getFaceDetails()
                .stream()
                .map(this::newFaceTag)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        metadata.add(new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc)
                .withTags(asTags(tags))
                .withRawKey(rawKey)
                .withDocumentProperties(singleton(propName))
                .build());
        return metadata;
    }

    /**
     * Create a AI Tag based on the face details.
     */
    protected AIMetadata.Tag newFaceTag(FaceDetail faceDetail) {
        BoundingBox box = faceDetail.getBoundingBox();
        if (faceDetail.getConfidence() >= minConfidence) {
            List<AIMetadata.Label> labels = collectLabels(faceDetail);
            return new AIMetadata.Tag("face", kind, null,
                    new AIMetadata.Box(box.getWidth(), box.getHeight(), box.getLeft(), box.getTop()),
                    labels,
                    faceDetail.getConfidence()
            );
        }
        return null;
    }

    /**
     * Adds extra labels for this face.
     */
    protected List<AIMetadata.Label> collectLabels(FaceDetail faceDetail, long timestamp) {
        List<AIMetadata.Label> labels = new ArrayList<>();
        if (faceDetail.getSmile() != null &&
                faceDetail.getSmile().getValue() &&
                faceDetail.getSmile().getConfidence() > minConfidence) {
            labels.add(new AIMetadata.Label("smile", faceDetail.getSmile().getConfidence() / 100, timestamp));
        }

        if (faceDetail.getEyeglasses() != null &&
                faceDetail.getEyeglasses().getValue() &&
                faceDetail.getEyeglasses().getConfidence() > minConfidence) {
            labels.add(new AIMetadata.Label("eyeglasses", faceDetail.getEyeglasses().getConfidence() / 100, timestamp));
        }

        if (faceDetail.getSunglasses() != null &&
                faceDetail.getSunglasses().getValue() &&
                faceDetail.getSunglasses().getConfidence() > minConfidence) {
            labels.add(new AIMetadata.Label("sunglasses", faceDetail.getSunglasses().getConfidence() / 100, timestamp));
        }

        if (faceDetail.getBeard() != null &&
                faceDetail.getBeard().getValue() &&
                faceDetail.getBeard().getConfidence() > minConfidence) {
            labels.add(new AIMetadata.Label("beard", faceDetail.getBeard().getConfidence() / 100, timestamp));
        }

        if (faceDetail.getMustache() != null &&
                faceDetail.getMustache().getValue() &&
                faceDetail.getMustache().getConfidence() > minConfidence) {
            labels.add(new AIMetadata.Label("mustache", faceDetail.getMustache().getConfidence() / 100, timestamp));
        }

        if (faceDetail.getEyesOpen() != null &&
                faceDetail.getEyesOpen().getValue() &&
                faceDetail.getEyesOpen().getConfidence() > minConfidence) {
            labels.add(new AIMetadata.Label("eyesOpen", faceDetail.getEyesOpen().getConfidence() / 100, timestamp));
        }

        if (faceDetail.getMouthOpen() != null &&
                faceDetail.getMouthOpen().getValue() &&
                faceDetail.getMouthOpen().getConfidence() > minConfidence) {
            labels.add(new AIMetadata.Label("mouthOpen", faceDetail.getMouthOpen().getConfidence() / 100, timestamp));
        }

        if (faceDetail.getGender() != null &&
                faceDetail.getGender().getConfidence() > minConfidence) {
            labels.add(new AIMetadata.Label(faceDetail.getGender().getValue().toLowerCase(),
                    faceDetail.getGender().getConfidence() / 100, timestamp));
        }

        if (faceDetail.getEmotions() != null && !faceDetail.getEmotions().isEmpty()) {
            faceDetail.getEmotions().forEach(emotion -> {
                if (emotion.getConfidence() > minConfidence) {
                    labels.add(new AIMetadata.Label(emotion.getType().toLowerCase(), emotion.getConfidence() / 100, timestamp));
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
