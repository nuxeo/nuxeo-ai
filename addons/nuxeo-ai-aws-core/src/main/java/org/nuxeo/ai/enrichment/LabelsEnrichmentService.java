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
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.Label;
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

import net.jodah.failsafe.RetryPolicy;

/**
 * Finds items in an image and labels them
 */
public class LabelsEnrichmentService extends AbstractEnrichmentService implements EnrichmentCachable {

    public static final String MINIMUM_CONFIDENCE = "minConfidence";

    public static final String DEFAULT_MAX_RESULTS = "200";

    public static final String DEFAULT_CONFIDENCE = "70";

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
        minConfidence = Float.parseFloat(options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
    }

    @SuppressWarnings("unchecked")
    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy().abortOn(SdkClientException.class);
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {

        List<EnrichmentMetadata> enriched = new ArrayList<>();
        try {
            for (Map.Entry<String, ManagedBlob> blob : blobTextFromDoc.getBlobs().entrySet()) {
                DetectLabelsResult result = Framework.getService(RekognitionService.class)
                                                     .detectLabels(blob.getValue(), maxResults, minConfidence);
                if (result != null && !result.getLabels().isEmpty()) {
                    enriched.addAll(processResult(blobTextFromDoc, blob.getKey(), result));
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
    protected Collection<EnrichmentMetadata> processResult(BlobTextFromDocument blobTextFromDoc, String propName,
                                                           DetectLabelsResult result) {
        List<EnrichmentMetadata.Label> labels = result.getLabels()
                                                      .stream()
                                                      .map(LabelsEnrichmentService::newLabel)
                                                      .filter(Objects::nonNull)
                                                      .collect(Collectors.toList());

        String raw = toJsonString(jg -> {
            jg.writeObjectField("labels", result.getLabels());
            jg.writeStringField("orientationCorrection", result.getOrientationCorrection());
        });

        String rawKey = saveJsonAsRawBlob(raw);
        return Collections.singletonList(new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc)
                                                 .withLabels(labels)
                                                 .withRawKey(rawKey)
                                                 .withDocumentProperties(singleton(propName))
                                                 .build());
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }
}
