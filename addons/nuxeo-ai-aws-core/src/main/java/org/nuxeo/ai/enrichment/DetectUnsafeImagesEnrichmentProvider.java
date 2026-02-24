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

import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.nuxeo.ai.aws.dto.LabelsResult;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;

/**
 * An enrichment provider for unsafe image detection - Now using domain DTOs
 */
public class DetectUnsafeImagesEnrichmentProvider extends AbstractConfidenceEnrichmentProvider {

    public static final String DEFAULT_CONFIDENCE = "50";

    @Override
    protected String getDefaultConfidence() {
        return DEFAULT_CONFIDENCE;
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {
        return enrichBlobs(blobTextFromDoc, (doc, propName, rs, blob) -> {
            LabelsResult result = rs.detectModerationLabels(blob, minConfidence);
            if (result != null && result.labels() != null && !result.labels().isEmpty()) {
                return processResult(doc, propName, result);
            }
            return Collections.emptyList();
        });
    }

    /**
     * Processes the result of the call to AWS
     */
    protected Collection<EnrichmentMetadata> processResult(BlobTextFromDocument blobTextFromDoc, String propName,
            LabelsResult result) {
        List<EnrichmentMetadata.Label> labels = result.labels()
                                                      .stream()
                                                      .filter(label -> label.confidence() >= minConfidence)
                                                      .map(label -> new EnrichmentMetadata.Label(label.name(),
                                                              label.confidence() / 100))
                                                      .collect(Collectors.toList());

        String raw = toJsonString(jg -> jg.writeObjectField("moderationLabels", result.labels()));
        String rawKey = saveJsonAsRawBlob(raw);

        return Collections.singletonList(buildLabelMetadata(blobTextFromDoc, propName, labels, rawKey));
    }
}
