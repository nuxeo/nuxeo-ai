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
 *     anechaev
 */
package org.nuxeo.ai.gcp.provider;

import static com.google.cloud.vision.v1.Feature.Type.LABEL_DETECTION;
import static java.util.Collections.singleton;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.enrichment.EnrichmentCachable;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.Feature;
import com.google.protobuf.util.JsonFormat;

import net.jodah.failsafe.RetryPolicy;

/**
 * Finds items in an image and labels them
 */
public class LabelsEnrichmentProvider extends AbstractTagProvider<EntityAnnotation> implements EnrichmentCachable {

    private static final Logger log = LogManager.getLogger(LabelsEnrichmentProvider.class);

    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy().abortOn(NuxeoException.class);
    }

    @Override
    protected Feature.Type getType() {
        return LABEL_DETECTION;
    }

    @Override
    protected List<EntityAnnotation> getAnnotationList(AnnotateImageResponse res) {
        return res.getLabelAnnotationsList();
    }

    @Override
    protected AIMetadata.Tag newTag(EntityAnnotation annotation) {
        throw new UnsupportedOperationException("The class is defined for label creation. See this#toLabel");
    }

    @Override
    protected List<EnrichmentMetadata> processResult(BlobTextFromDocument doc, List<ManagedBlob> blobs,
            List<AnnotateImageResponse> responses) {
        Iterator<ManagedBlob> iterator = blobs.iterator();
        List<EnrichmentMetadata> results = new ArrayList<>();
        for (AnnotateImageResponse res : responses) {
            if (res.hasError()) {
                log.error(res.getError().getMessage());
                continue;
            }

            List<EntityAnnotation> annotations = getAnnotationList(res);
            List<AIMetadata.Label> labels = annotations.stream()
                                                       .map(this::toLabel)
                                                       .filter(label -> label.getConfidence() >= minConfidence)
                                                       .collect(Collectors.toList());

            String raw = toJsonString(jg -> jg.writeObjectField("labels", JsonFormat.printer().print(res)));
            String rawKey = saveJsonAsRawBlob(raw);

            EnrichmentMetadata build = new EnrichmentMetadata.Builder(kind, name, doc).withLabels(asLabels(labels))
                                                                                      .withRawKey(rawKey)
                                                                                      .withDocumentProperties(singleton(
                                                                                              iterator.next().getKey()))
                                                                                      .build();
            results.add(build);
        }

        return results;
    }

    protected EnrichmentMetadata.Label toLabel(EntityAnnotation annotation) {
        float score = annotation.getScore();
        String desc = annotation.getDescription();

        return new EnrichmentMetadata.Label(desc, score);
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }
}
