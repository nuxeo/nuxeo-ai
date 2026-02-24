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

import org.nuxeo.ai.aws.dto.TextDetectionResult;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;

/**
 * An enrichment provider for text detection in images - Now using domain DTOs
 */
public class DetectTextEnrichmentProvider extends AbstractConfidenceEnrichmentProvider {

    public static final String DEFAULT_CONFIDENCE = "80";

    @Override
    protected String getDefaultConfidence() {
        return DEFAULT_CONFIDENCE;
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {
        return enrichBlobs(blobTextFromDoc, (doc, propName, rs, blob) -> {
            TextDetectionResult result = rs.detectText(blob);
            if (result != null && result.textDetections() != null && !result.textDetections().isEmpty()) {
                return processResult(doc, propName, result);
            }
            return Collections.emptyList();
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

        return Collections.singletonList(buildLabelMetadata(blobTextFromDoc, propName, labels, rawKey));
    }
}
