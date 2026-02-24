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

import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueStore;

import software.amazon.awssdk.services.rekognition.model.FaceDetection;
import software.amazon.awssdk.services.rekognition.model.GetFaceDetectionRequest;
import software.amazon.awssdk.services.rekognition.model.GetFaceDetectionResponse;

/**
 * Detects faces in a video asynchronously.
 */
public class DetectFacesEnrichmentProvider
        extends org.nuxeo.ai.enrichment.DetectFacesEnrichmentProvider {

    public static final String ASYNC_ACTION_NAME = "StartFaceDetection";

    public static final String ENRICHMENT_NAME = "aws.videoFaceDetection";

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
     * Processes the result of the async call to AWS.
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

}
