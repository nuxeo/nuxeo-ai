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

import static java.util.Collections.emptyList;
import static org.nuxeo.ai.enrichment.LabelsEnrichmentService.MINIMUM_CONFIDENCE;
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.toJsonString;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.rekognition.model.BoundingBox;
import com.amazonaws.services.rekognition.model.Celebrity;
import com.amazonaws.services.rekognition.model.ComparedFace;
import com.amazonaws.services.rekognition.model.RecognizeCelebritiesResult;

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
    public Collection<EnrichmentMetadata> enrich(BlobTextStream blobTextStream) {
        RecognizeCelebritiesResult result;
        try {
            result = Framework.getService(RekognitionService.class).detectCelebrityFaces(blobTextStream.getBlob());
        } catch (AmazonClientException e) {
            throw new NuxeoException(e);
        }

        if (result != null && (!result.getCelebrityFaces().isEmpty() || !result.getUnrecognizedFaces().isEmpty())) {
            return processResults(blobTextStream, result);
        }
        return emptyList();
    }

    /**
     * Processes the result of the call to AWS
     */
    protected Collection<EnrichmentMetadata> processResults(BlobTextStream blobTextStream, RecognizeCelebritiesResult result) {
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
        return Collections.singletonList(new EnrichmentMetadata.Builder(kind, name, blobTextStream)
                                                 .withRawKey(rawKey)
                                                 .withTags(tags)
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
