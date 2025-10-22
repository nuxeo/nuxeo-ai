/*
 *   (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *
 *   Contributors:
 *       anechaev
 */
package org.nuxeo.ai.gcp.provider;

import static com.google.cloud.vision.v1.Feature.Type.FACE_DETECTION;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.enrichment.EnrichmentCachable;
import org.nuxeo.ai.enrichment.EnrichmentDescriptor;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.NuxeoException;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.FaceAnnotation;
import com.google.cloud.vision.v1.Feature;

import net.jodah.failsafe.RetryPolicy;

/**
 * Finds items in an image and labels them
 */
public class FaceEnrichmentProvider extends AbstractTagProvider<FaceAnnotation>
        implements EnrichmentCachable, Polygonal {

    private static final Logger log = LogManager.getLogger(FaceEnrichmentProvider.class);

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        // GCP doesn't provide confidence for face detection
        this.minConfidence = 0.0f;
    }

    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy().abortOn(NuxeoException.class);
    }

    @Override
    protected Feature.Type getType() {
        return FACE_DETECTION;
    }

    @Override
    protected List<FaceAnnotation> getAnnotationList(AnnotateImageResponse response) {
        return response.getFaceAnnotationsList();
    }

    @Override
    protected AIMetadata.Tag newTag(FaceAnnotation annotation) {
        // Face detection doesn't have a confidence score, so we use 1.0f
        return new AIMetadata.Tag("Face", null, null, null, java.util.Collections.emptyList(), 1.0f);
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }
}
