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
 *       Thibaud Arguillere
 */
package org.nuxeo.ai.gcp.provider;

import static com.google.cloud.vision.v1.Feature.Type.SAFE_SEARCH_DETECTION;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.enrichment.EnrichmentCachable;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.NuxeoException;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Likelihood;
import com.google.cloud.vision.v1.SafeSearchAnnotation;

import net.jodah.failsafe.RetryPolicy;

/**
 * Categorize the SafeSearch properties of an image
 * Values are not normalized with the values returned with AWS, which
 * are way more detailed (https://docs.aws.amazon.com/rekognition/latest/dg/moderation.html)
 * Also, there is no global minimum confidence here, we always return all results (adult, violence, ...).
 * 
 * For each value, as Google deprecated the confidence level, we pa the Likelihood to a confidence:
 * see {@code getConfidenceFromLikelihood}
 * 
 */
public class SafeSearchEnrichmentProvider extends AbstractTagProvider<SafeSearchAnnotation>
        implements EnrichmentCachable, Polygonal {

    private static final Logger log = LogManager.getLogger(SafeSearchEnrichmentProvider.class);

    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy().abortOn(NuxeoException.class);
    }

    @Override
    protected Feature.Type getType() {
        return SAFE_SEARCH_DETECTION;
    }

    @Override
    protected List<SafeSearchAnnotation> getAnnotationList(AnnotateImageResponse res) {
    	ArrayList<SafeSearchAnnotation> result = new ArrayList<SafeSearchAnnotation>();
    	result.add(res.getSafeSearchAnnotation());
    	return result;   
    }

    /**
     * Create a normalized tag
     * 
     * Google deprecated the confidence for the misc. fields of the SafeSearchAnnotation
     * (getConfidence() always returns 0.0). We map the value (LIKELY, UNLIKELY, …) to
     * a confidence.
     */
    @Override
    protected AIMetadata.Tag newTag(SafeSearchAnnotation annotation) {
    	
        List<AIMetadata.Label> labels = new ArrayList<>();
        labels.add(new AIMetadata.Label("adult", getConfidenceFromLikelihood(annotation.getAdult())));
        labels.add(new AIMetadata.Label("medical", getConfidenceFromLikelihood(annotation.getMedical())));
        labels.add(new AIMetadata.Label("racy", getConfidenceFromLikelihood(annotation.getRacy())));
        labels.add(new AIMetadata.Label("spoof", getConfidenceFromLikelihood(annotation.getSpoof())));
        labels.add(new AIMetadata.Label("violence", getConfidenceFromLikelihood(annotation.getViolence())));
        
        return new EnrichmentMetadata.Tag("safeSearch", kind, null, null, labels, 0);
    }
    
    protected float getConfidenceFromLikelihood(Likelihood value) {
    	
    	switch (value) {
    	case VERY_UNLIKELY:
    		return 0.0f;
    		
    	case UNLIKELY:
    		return 0.25f;
    		
    	case POSSIBLE:
    		return 0.5f;
    		
    	case LIKELY:
    		return 0.75f;
    		
    	case VERY_LIKELY:
    		return 0.9f;
    		
    	case UNKNOWN:
    	case UNRECOGNIZED:
    	default:
    		return 0.0f;
    	}
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }
}
