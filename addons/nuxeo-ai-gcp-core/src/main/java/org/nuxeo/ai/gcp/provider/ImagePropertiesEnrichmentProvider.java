/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
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
 *     mvachette
 */
package org.nuxeo.ai.gcp.provider;

import static com.google.cloud.vision.v1.Feature.Type.IMAGE_PROPERTIES;
import static java.util.Collections.singleton;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import java.util.ArrayList;
import java.util.Collections;
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
import com.google.cloud.vision.v1.ColorInfo;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.ImageProperties;
import com.google.protobuf.util.JsonFormat;
import com.google.type.Color;

import net.jodah.failsafe.RetryPolicy;

/**
 * Finds items in an image and labels them
 */
public class ImagePropertiesEnrichmentProvider extends AbstractTagProvider<ImageProperties>
        implements EnrichmentCachable {

    private static final Logger log = LogManager.getLogger(ImagePropertiesEnrichmentProvider.class);

    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy().abortOn(NuxeoException.class);
    }

    @Override
    protected Feature.Type getType() {
        return IMAGE_PROPERTIES;
    }

    @Override
    protected List<ImageProperties> getAnnotationList(AnnotateImageResponse res) {
        return Collections.singletonList(res.getImagePropertiesAnnotation());
    }

    @Override
    protected AIMetadata.Tag newTag(ImageProperties imageProperties) {
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

            List<ImageProperties> annotations = getAnnotationList(res);
            if (annotations.isEmpty() || !annotations.get(0).hasDominantColors()) {
                continue;
            }

            List<ColorInfo> colorInfos = annotations.get(0).getDominantColors().getColorsList();
            List<AIMetadata.Label> labels = colorInfos.stream()
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

    protected EnrichmentMetadata.Label toLabel(ColorInfo colorInfo) {
        float score = colorInfo.getScore();
        Color color = colorInfo.getColor();
        String desc = String.format("%f,%f,%f,%f", color.getRed(), color.getGreen(), color.getBlue(),
                colorInfo.getPixelFraction());
        return new EnrichmentMetadata.Label(desc, score);
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }
}
