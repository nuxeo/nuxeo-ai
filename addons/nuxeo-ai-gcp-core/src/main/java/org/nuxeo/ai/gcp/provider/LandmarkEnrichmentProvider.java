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
package org.nuxeo.ai.gcp.provider;

import static com.google.cloud.vision.v1.Feature.Type.LANDMARK_DETECTION;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.enrichment.EnrichmentCachable;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.NuxeoException;

import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.Feature;

import net.jodah.failsafe.RetryPolicy;

/**
 * Finds items in an image and labels them
 */
public class LandmarkEnrichmentProvider extends AbstractTagProvider<EntityAnnotation>
        implements EnrichmentCachable, Polygonal {

    private static final Logger log = LogManager.getLogger(LandmarkEnrichmentProvider.class);

    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy().abortOn(NuxeoException.class);
    }

    @Override
    protected Feature.Type getType() {
        return LANDMARK_DETECTION;
    }

    @Override
    protected List<EntityAnnotation> getAnnotationList(AnnotateImageResponse res) {
        return res.getLandmarkAnnotationsList();
    }

    /**
     * Create a normalized tag
     */
    @Override
    protected AIMetadata.Tag newTag(EntityAnnotation annotation) {
        AIMetadata.Box box = getBox(annotation.getBoundingPoly());
        return new EnrichmentMetadata.Tag(annotation.getDescription(), kind, null, box, null, annotation.getScore());
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }
}
