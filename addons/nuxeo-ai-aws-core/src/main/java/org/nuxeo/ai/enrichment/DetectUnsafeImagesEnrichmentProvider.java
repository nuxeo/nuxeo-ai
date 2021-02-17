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

import com.amazonaws.SdkClientException;
import com.amazonaws.services.rekognition.model.DetectModerationLabelsResult;
import com.amazonaws.services.rekognition.model.ModerationLabel;
import net.jodah.failsafe.RetryPolicy;
import org.nuxeo.ai.AWSHelper;
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

import static java.util.Collections.singleton;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

/**
 * Detect unsafe content in images.
 */
public class DetectUnsafeImagesEnrichmentProvider extends AbstractEnrichmentProvider implements EnrichmentCachable {

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

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument doc) {
        RekognitionService rs = Framework.getService(RekognitionService.class);
        return AWSHelper.handlingExceptions(() -> {
            List<EnrichmentMetadata> enriched = new ArrayList<>();
            for (Map.Entry<String, ManagedBlob> blob : doc.getBlobs().entrySet()) {
                DetectModerationLabelsResult result = rs.detectUnsafeImages(blob.getValue());
                if (result != null && !result.getModerationLabels().isEmpty()) {
                    enriched.addAll(processResult(doc, blob.getKey(), result));
                }
            }
            return enriched;
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy().abortOn(SdkClientException.class);
    }

    /**
     * Processes the result of the call to AWS
     */
    protected Collection<EnrichmentMetadata> processResult(BlobTextFromDocument blobTextFromDoc, String propName,
            DetectModerationLabelsResult result) {
        List<EnrichmentMetadata.Label> labels = result.getModerationLabels()
                                                      .stream()
                                                      .map(this::newLabel)
                                                      .filter(Objects::nonNull)
                                                      .collect(Collectors.toList());

        String raw = toJsonString(jg -> jg.writeObjectField("labels", result.getModerationLabels()));

        String rawKey = saveJsonAsRawBlob(raw);
        return Collections.singletonList(
                new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc).withLabels(asLabels(labels))
                                                                           .withRawKey(rawKey)
                                                                           .withDocumentProperties(singleton(propName))
                                                                           .build());
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }
}
