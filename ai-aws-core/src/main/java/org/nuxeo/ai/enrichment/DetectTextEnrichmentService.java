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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.rekognition.model.BoundingBox;
import com.amazonaws.services.rekognition.model.DetectTextResult;
import com.amazonaws.services.rekognition.model.TextDetection;

/**
 * Detects words and lines in an image.
 */
public class DetectTextEnrichmentService extends AbstractEnrichmentService {

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
        if (textD.getConfidence() >= minConfidence && textTypes.contains(textD.getType())) {
            BoundingBox box = textD.getGeometry().getBoundingBox();
            return new EnrichmentMetadata.Tag(textD.getDetectedText(), kind, null,
                                              new AIMetadata.Box(box.getWidth(), box.getHeight(), box.getLeft(), box
                                                      .getTop()),
                                              null, textD.getConfidence() / 100);
        }
        return null;
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextStream blobTextStream) {
        DetectTextResult result;
        try {
            result = Framework.getService(RekognitionService.class).detectText(blobTextStream.getBlob());
        } catch (AmazonClientException e) {
            throw new NuxeoException(e);
        }

        if (result != null && !result.getTextDetections().isEmpty()) {
            return processResults(blobTextStream, result);
        }

        return emptyList();
    }

    /**
     * Processes the result of the call to AWS
     */
    protected Collection<EnrichmentMetadata> processResults(BlobTextStream blobTextStream, DetectTextResult result) {
        List<AIMetadata.Tag> tags = result.getTextDetections()
                                          .stream()
                                          .map(this::newTag)
                                          .filter(Objects::nonNull)
                                          .collect(Collectors.toList());
        String raw = toJsonString(jg -> jg.writeObjectField("textDetections", result.getTextDetections()));
        String rawKey = saveJsonAsRawBlob(raw);
        return Collections.singletonList(new EnrichmentMetadata.Builder(kind, name, blobTextStream)
                                                 .withRawKey(rawKey)
                                                 .withTags(tags)
                                                 .build());
    }


}
