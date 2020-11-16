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

import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.CropHint;
import com.google.cloud.vision.v1.CropHintsParams;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.ImageContext;
import net.jodah.failsafe.RetryPolicy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.enrichment.EnrichmentCachable;
import org.nuxeo.ai.enrichment.EnrichmentDescriptor;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.NuxeoException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.cloud.vision.v1.Feature.Type.CROP_HINTS;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;

/**
 * Finds items in an image and labels them
 */
public class CropHintsEnrichmentProvider extends AbstractTagProvider<CropHint> implements EnrichmentCachable, Polygonal {

    private static final Logger log = LogManager.getLogger(CropHintsEnrichmentProvider.class);

    protected List<Float> ratios;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        Map<String, String> options = descriptor.options;
        String ratioOption = options.getOrDefault("ratios", "");
        ratios = Arrays.stream(ratioOption.split(",")).map(Float::parseFloat)
                .collect(Collectors.toList());
    }

    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy().abortOn(NuxeoException.class);
    }

    @Override
    protected Feature.Type getType() {
        return CROP_HINTS;
    }

    @Override
    protected ImageContext getImageContext() {
        CropHintsParams cropHintsParams = CropHintsParams.newBuilder().addAllAspectRatios(ratios).build();
        return ImageContext.newBuilder().setCropHintsParams(cropHintsParams).build();
    }

    @Override
    protected List<CropHint> getAnnotationList(AnnotateImageResponse res) {
        return res.hasCropHintsAnnotation() ?
                res.getCropHintsAnnotation().getCropHintsList() : new ArrayList<>();
    }

    @Override
    protected AIMetadata.Tag newTag(CropHint cropHint) {
        AIMetadata.Box box = getBox(cropHint.getBoundingPoly());
        String ratio = String.format("%.2f", box.width / box.height);
        return new EnrichmentMetadata.Tag(ratio, kind, null, box, null, cropHint.getConfidence());
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }
}
