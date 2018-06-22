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
import java.util.stream.Collectors;

import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;

import com.amazonaws.AmazonClientException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.Label;

import net.jodah.failsafe.RetryPolicy;

/**
 * Finds items in an image and labels them
 */
public class LabelsEnrichmentService extends AbstractEnrichmentService {

    public static final String MINIMUM_CONFIDENCE = "minConfidence";
    public static final String DEFAULT_MAX_RESULTS = "200";
    protected int maxResults;
    protected float minConfidence;

    protected static EnrichmentMetadata.Label newLabel(Label l) {
        return new EnrichmentMetadata.Label(l.getName(), l.getConfidence() / 100);
    }

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        Map<String, String> options = descriptor.options;
        maxResults = Integer.parseInt(options.getOrDefault(MAX_RESULTS, DEFAULT_MAX_RESULTS));
        minConfidence = Float.parseFloat(options.getOrDefault(MINIMUM_CONFIDENCE, "70"));
    }

    @SuppressWarnings("unchecked")
    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy().abortOn(SdkClientException.class);
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextStream blobTextStream) {
        RekognitionService rekognitionService = Framework.getService(RekognitionService.class);
        DetectLabelsResult result;
        try {
            result = rekognitionService.detectLabels(blobTextStream.getBlob(), maxResults, minConfidence);
        } catch (AmazonClientException e) {
            throw new NuxeoException(e);
        }

        if (result != null && !result.getLabels().isEmpty()) {
            List<EnrichmentMetadata.Label> labels = result.getLabels()
                                                          .stream()
                                                          .map(LabelsEnrichmentService::newLabel)
                                                          .collect(Collectors.toList());

            String raw = toJsonString(jg -> {
                jg.writeObjectField("labels", result.getLabels());
                jg.writeStringField("orientationCorrection", result.getOrientationCorrection());
            });

            String rawKey = saveJsonAsRawBlob(raw);
            return Collections.singletonList(new EnrichmentMetadata.Builder(kind, name, blobTextStream)
                                                     .withRawKey(rawKey)
                                                     .withLabels(labels)
                                                     .build());
        }
        return emptyList();
    }

}
