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
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.toJsonString;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;

import com.amazonaws.AmazonClientException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.rekognition.model.DetectModerationLabelsResult;
import com.amazonaws.services.rekognition.model.ModerationLabel;

import net.jodah.failsafe.RetryPolicy;

/**
 * Detect unsafe content in images.
 */
public class DetectUnsafeImagesEnrichmentService extends AbstractEnrichmentService {

    public static final String MINIMUM_CONFIDENCE = "minConfidence";

    public static final String DEFAULT_CONFIDENCE = "70";

    protected float minConfidence;

    protected EnrichmentMetadata.Label newLabel(ModerationLabel l) {
        if (l.getConfidence() >= minConfidence) {
            return new EnrichmentMetadata.Label(l.getName(), l.getConfidence() / 100);
        } else {
            return null;
        }
    }

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        Map<String, String> options = descriptor.options;
        minConfidence = Float.parseFloat(options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
    }

    @SuppressWarnings("unchecked")
    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy().abortOn(SdkClientException.class);
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextStream blobTextStream) {
        DetectModerationLabelsResult result;
        try {
            result = Framework.getService(RekognitionService.class).detectUnsafeImages(blobTextStream.getBlob());
        } catch (AmazonClientException e) {
            throw new NuxeoException(e);
        }

        if (result != null && !result.getModerationLabels().isEmpty()) {
            return processResult(blobTextStream, result);
        }
        return emptyList();
    }

    /**
     * Processes the result of the call to AWS
     */
    protected Collection<EnrichmentMetadata> processResult(BlobTextStream blobTextStream, DetectModerationLabelsResult result) {
        List<EnrichmentMetadata.Label> labels = result.getModerationLabels()
                                                      .stream()
                                                      .map(this::newLabel)
                                                      .filter(Objects::nonNull)
                                                      .collect(Collectors.toList());

        String raw = toJsonString(jg -> jg.writeObjectField("labels", result.getModerationLabels()));

        String rawKey = saveJsonAsRawBlob(raw);
        return Collections.singletonList(new EnrichmentMetadata.Builder(kind, name, blobTextStream)
                                                 .withLabels(labels)
                                                 .withRawKey(rawKey)
                                                 .build());
    }

}
