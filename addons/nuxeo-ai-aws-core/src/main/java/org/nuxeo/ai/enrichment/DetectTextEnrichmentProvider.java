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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import software.amazon.awssdk.services.rekognition.model.DetectTextResponse;
import software.amazon.awssdk.services.rekognition.model.TextDetection;

/**
 * Detects words and lines in an image.
 */
public class DetectTextEnrichmentProvider extends AbstractEnrichmentProvider implements EnrichmentCachable {

    public static final String TEXT_TYPES = "textTypes";

    public static final String DEFAULT_CONFIDENCE = "70";

    public static final String DEFAULT_TEXT_TYPES = "LINE,WORD";

    protected float minConfidence;

    protected Set<String> textTypes;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        Map<String, String> options = descriptor.options;
        String textList = options.getOrDefault(TEXT_TYPES, DEFAULT_TEXT_TYPES);
        textTypes = new HashSet<>(Arrays.asList(textList.split(",")));
        minConfidence = Float.parseFloat(options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
    }

    /**
     * Create a normalized tag
     */
    protected AIMetadata.Tag newTag(TextDetection textD) {
        if (textD.confidence() >= minConfidence && textTypes.contains(textD.typeAsString())) {
            software.amazon.awssdk.services.rekognition.model.BoundingBox box = textD.geometry().boundingBox();
            return new EnrichmentMetadata.Tag(textD.detectedText(), kind, null,
                    new AIMetadata.Box(box.width(), box.height(), box.left(), box.top()), null,
                    textD.confidence() / 100);
        }
        return null;
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {
        return AWSHelper.handlingExceptions(() -> {
            List<EnrichmentMetadata> enriched = new ArrayList<>();
            for (Map.Entry<String, ManagedBlob> blob : blobTextFromDoc.getBlobs().entrySet()) {
                DetectTextResponse result = Framework.getService(RekognitionService.class).detectText(blob.getValue());
                if (result != null && !result.textDetections().isEmpty()) {
                    enriched.addAll(processResults(blobTextFromDoc, blob.getKey(), result));
                }
            }
            return enriched;
        });
    }

    /**
     * Processes the result of the call to AWS
     */
    protected Collection<EnrichmentMetadata> processResults(BlobTextFromDocument blobTextFromDoc, String propName,
            DetectTextResponse result) {
        List<AIMetadata.Tag> tags = result.textDetections()
                                          .stream()
                                          .map(this::newTag)
                                          .filter(Objects::nonNull)
                                          .collect(Collectors.toList());
        String raw = toJsonString(jg -> jg.writeObjectField("textDetections", result.textDetections()));
        String rawKey = saveJsonAsRawBlob(raw);
        return Collections.singletonList(
                new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc).withTags(asTags(tags))
                                                                           .withRawKey(rawKey)
                                                                           .withDocumentProperties(singleton(propName))
                                                                           .build());
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }

}
