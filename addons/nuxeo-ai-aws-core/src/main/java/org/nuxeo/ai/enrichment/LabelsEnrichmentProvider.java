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

import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.aws.dto.LabelsResult;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import software.amazon.awssdk.core.exception.SdkClientException;

import net.jodah.failsafe.RetryPolicy;

/**
 * Finds items in an image and labels them - Now using domain DTOs
 */
public class LabelsEnrichmentProvider extends AbstractEnrichmentProvider implements EnrichmentCachable {

    public static final String MINIMUM_CONFIDENCE = "minConfidence";

    public static final String DEFAULT_MAX_RESULTS = "200";

    public static final String DEFAULT_CONFIDENCE = "70";

    protected int maxResults = Integer.parseInt(DEFAULT_MAX_RESULTS);

    protected float minConfidence = Float.parseFloat(DEFAULT_CONFIDENCE);

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        maxResults = Integer.parseInt(descriptor.options.getOrDefault(MAX_RESULTS, DEFAULT_MAX_RESULTS));
        minConfidence = Float.parseFloat(descriptor.options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {
        return AWSHelper.handlingExceptions(() -> {
            List<EnrichmentMetadata> enriched = new ArrayList<>();
            RekognitionService rs = Framework.getService(RekognitionService.class);
            for (Map.Entry<String, ManagedBlob> blob : blobTextFromDoc.getBlobs().entrySet()) {
                LabelsResult result = rs.detectLabels(blob.getValue(), maxResults, minConfidence);
                if (result != null && result.getLabels() != null && !result.getLabels().isEmpty()) {
                    enriched.addAll(processResult(blobTextFromDoc, blob.getKey(), result));
                }
            }
            return enriched;
        });
    }

    /**
     * Processes the result of the call to AWS
     */
    protected Collection<EnrichmentMetadata> processResult(BlobTextFromDocument blobTextFromDoc, String propName,
            LabelsResult result) {
        List<EnrichmentMetadata.Label> labels = result.getLabels().stream()
                .map(awsLabel -> new EnrichmentMetadata.Label(awsLabel.getName(), awsLabel.getConfidence() / 100))
                .collect(Collectors.toList());

        String raw = toJsonString(jg -> jg.writeObjectField("labels", result.getLabels()));
        String rawKey = saveJsonAsRawBlob(raw);

        return java.util.Collections.singletonList(
                new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc)
                        .withLabels(asLabels(labels))
                        .withRawKey(rawKey)
                        .withDocumentProperties(java.util.Collections.singleton(propName))
                        .build());
    }

    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy()
                    .abortOn(throwable -> throwable instanceof SdkClientException &&
                            throwable.getMessage().contains("is not authorized to perform"));
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }
}
