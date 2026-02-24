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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;

import software.amazon.awssdk.services.rekognition.model.BoundingBox;
import software.amazon.awssdk.services.rekognition.model.Celebrity;
import software.amazon.awssdk.services.rekognition.model.ComparedFace;
import software.amazon.awssdk.services.rekognition.model.RecognizeCelebritiesResponse;

/**
 * Detects celebrity faces in an image
 */
public class DetectCelebritiesEnrichmentProvider extends AbstractEnrichmentProvider implements EnrichmentCachable {

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
        return AWSHelper.handlingExceptions(() -> {
            List<EnrichmentMetadata> enriched = new ArrayList<>();
            for (Map.Entry<String, ManagedBlob> blob : doc.getBlobs().entrySet()) {
                RecognizeCelebritiesResponse result = rs.detectCelebrities(blob.getValue());
                if (result != null && (!result.celebrityFaces().isEmpty() || !result.unrecognizedFaces().isEmpty())) {
                    enriched.addAll(processResults(doc, blob.getKey(), result));
                }
            }
            return enriched;
        });
    }

    /**
     * Processes the result of the call to AWS
     */
    protected Collection<EnrichmentMetadata> processResults(BlobTextFromDocument blobTextFromDoc, String propName,
            RecognizeCelebritiesResponse result) {
        List<AIMetadata.Tag> tags = Stream.concat(result.celebrityFaces().stream().map(this::newCelebrityTag),
                result.unrecognizedFaces().stream().map(this::newFaceTag))
                                          .filter(Objects::nonNull)
                                          .collect(Collectors.toList());

        String raw = toJsonString(jg -> {
            jg.writeObjectField("celebrityFaces", result.celebrityFaces());
            jg.writeObjectField("unrecognizedFaces", result.unrecognizedFaces());
            if (result.orientationCorrection() != null) {
                jg.writeStringField("orientationCorrection", result.orientationCorrection().toString());
            }
        });

        String rawKey = saveJsonAsRawBlob(raw);
        return Collections.singletonList(
                new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc).withTags(asTags(tags))
                                                                           .withRawKey(rawKey)
                                                                           .withDocumentProperties(singleton(propName))
                                                                           .build());
    }

    /**
     * Create a AI Tag based on the celebrity face.
     */
    protected AIMetadata.Tag newCelebrityTag(Celebrity celebrity) {
        BoundingBox box = celebrity.face().boundingBox();
        if (celebrity.matchConfidence() >= minConfidence) {
            return new AIMetadata.Tag(celebrity.name(), kind, celebrity.id(),
                    new AIMetadata.Box(box.width(), box.height(), box.left(), box.top()), null,
                    celebrity.matchConfidence() / 100);
        }
        return null;
    }

    /**
     * Create a AI Tag based on the unrecognized face.
     */
    protected AIMetadata.Tag newFaceTag(ComparedFace faceDetail) {
        BoundingBox box = faceDetail.boundingBox();
        if (faceDetail.confidence() >= minConfidence) {
            return new AIMetadata.Tag("face", "/tagging/face", null,
                    new AIMetadata.Box(box.width(), box.height(), box.left(), box.top()), null,
                    faceDetail.confidence() / 100);
        }
        return null;
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }
}
