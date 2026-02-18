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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.aws.dto.TextDetectionResult;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;

import net.jodah.failsafe.RetryPolicy;
import software.amazon.awssdk.core.exception.SdkClientException;

/**
 * An enrichment provider for text detection in images - Now using domain DTOs
 */
public class DetectTextEnrichmentProvider extends AbstractEnrichmentProvider implements EnrichmentCachable {

    public static final String MINIMUM_CONFIDENCE = "minConfidence";

    public static final String DEFAULT_CONFIDENCE = "80";

    private static final float DEFAULT_CONFIDENCE_FLOAT = 80f;

    protected float minConfidence = parseConfidence(DEFAULT_CONFIDENCE);

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        minConfidence = parseConfidence(descriptor.options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
    }

    /**
     * Safely parses a confidence value string to float, returning a default value on error.
     */
    private float parseConfidence(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return DEFAULT_CONFIDENCE_FLOAT;
        }
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {
        return AWSHelper.handlingExceptions(() -> {
            List<EnrichmentMetadata> enriched = new ArrayList<>();
            RekognitionService rs = Framework.getService(RekognitionService.class);
            for (Map.Entry<String, ManagedBlob> blob : blobTextFromDoc.getBlobs().entrySet()) {
                TextDetectionResult result = rs.detectText(blob.getValue());
                if (result != null && result.textDetections() != null && !result.textDetections().isEmpty()) {
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
            TextDetectionResult result) {
        List<EnrichmentMetadata.Label> labels = result.textDetections()
                                                      .stream()
                                                      .filter(textD -> textD.confidence() >= minConfidence)
                                                      .map(textD -> {
                                                          var box = textD.boundingBox();
                                                          String labelText = textD.detectedText();
                                                          if (box != null) {
                                                              labelText += String.format(" [%.3f,%.3f,%.3f,%.3f]",
                                                                      box.left(), box.top(), box.width(), box.height());
                                                          }
                                                          return new EnrichmentMetadata.Label(labelText,
                                                                  textD.confidence() / 100);
                                                      })
                                                      .toList();

        String raw = toJsonString(jg -> jg.writeObjectField("textDetections", result.textDetections()));
        String rawKey = saveJsonAsRawBlob(raw);

        return Collections.singletonList(
                new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc).withLabels(asLabels(labels))
                                                                           .withRawKey(rawKey)
                                                                           .withDocumentProperties(
                                                                                   Collections.singleton(propName))
                                                                           .build());
    }

    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy().abortOn(throwable -> throwable instanceof SdkClientException
                && throwable.getMessage().contains("is not authorized to perform"));
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }
}
