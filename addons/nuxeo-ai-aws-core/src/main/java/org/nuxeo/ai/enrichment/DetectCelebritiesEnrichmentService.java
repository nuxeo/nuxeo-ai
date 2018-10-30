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
import static org.nuxeo.ai.enrichment.LabelsEnrichmentService.MINIMUM_CONFIDENCE;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.rekognition.model.BoundingBox;
import com.amazonaws.services.rekognition.model.Celebrity;
import com.amazonaws.services.rekognition.model.ComparedFace;
import com.amazonaws.services.rekognition.model.RecognizeCelebritiesResult;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Detects celebrity faces in an image
 */
public class DetectCelebritiesEnrichmentService extends AbstractEnrichmentService {

    public static final String DEFAULT_CONFIDENCE = "70";

    protected float minConfidence;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        minConfidence = Float.parseFloat(descriptor.options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {

        List<EnrichmentMetadata> enriched = new ArrayList<>();
        try {
            for (Map.Entry<String, ManagedBlob> blob : blobTextFromDoc.getBlobs().entrySet()) {
                RecognizeCelebritiesResult result =
                        Framework.getService(RekognitionService.class).detectCelebrityFaces(blob.getValue());
                if (result != null &&
                        (!result.getCelebrityFaces().isEmpty() || !result.getUnrecognizedFaces().isEmpty())) {
                    enriched.addAll(processResults(blobTextFromDoc, blob.getKey(), result));
                }
            }
            return enriched;
        } catch (AmazonServiceException e) {
            throw EnrichmentHelper.isFatal(e) ? new FatalEnrichmentError(e) : e;
        }
    }

    /**
     * Processes the result of the call to AWS
     */
    protected Collection<EnrichmentMetadata> processResults(BlobTextFromDocument blobTextFromDoc, String propName,
                                                            RecognizeCelebritiesResult result) {
        List<AIMetadata.Tag> tags = Stream.concat(
                result.getCelebrityFaces().stream().map(this::newCelebrityTag),
                result.getUnrecognizedFaces().stream().map(this::newFaceTag)
        ).filter(Objects::nonNull).collect(Collectors.toList());

        String raw = toJsonString(jg -> {
            jg.writeObjectField("celebrityFaces", result.getCelebrityFaces());
            jg.writeObjectField("unrecognizedFaces", result.getUnrecognizedFaces());
            jg.writeStringField("orientationCorrection", result.getOrientationCorrection());
        });

        String rawKey = saveJsonAsRawBlob(raw);
        return Collections.singletonList(new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc)
                                                 .withTags(tags)
                                                 .withRawKey(rawKey)
                                                 .withDocumentProperties(singleton(propName))
                                                 .build());
    }

    /**
     * Create a AI Tag based on the celebrity face.
     */
    protected AIMetadata.Tag newCelebrityTag(Celebrity celebrity) {
        BoundingBox box = celebrity.getFace().getBoundingBox();
        if (celebrity.getMatchConfidence() >= minConfidence) {
            return new AIMetadata.Tag(celebrity.getName(), kind, celebrity.getId(),
                                      new AIMetadata.Box(box.getWidth(), box.getHeight(), box.getLeft(), box.getTop()),
                                      null,
                                      celebrity.getMatchConfidence() / 100
            );
        }
        return null;
    }

    /**
     * Create a AI Tag based on the unrecognized face.
     */
    protected AIMetadata.Tag newFaceTag(ComparedFace faceDetail) {
        BoundingBox box = faceDetail.getBoundingBox();
        if (faceDetail.getConfidence() >= minConfidence) {
            return new AIMetadata.Tag("face", "/tagging/face", null,
                                      new AIMetadata.Box(box.getWidth(), box.getHeight(), box.getLeft(), box.getTop()),
                                      null,
                                      faceDetail.getConfidence() / 100
            );
        }
        return null;
    }
}
