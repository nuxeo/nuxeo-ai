/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.rekognition.listeners;

import static org.nuxeo.ai.enrichment.async.DetectFacesEnrichmentProvider.ENRICHMENT_NAME;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.async.DetectFacesEnrichmentProvider;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.runtime.api.Framework;

import com.amazonaws.services.rekognition.model.GetFaceDetectionRequest;
import com.amazonaws.services.rekognition.model.GetFaceDetectionResult;

/**
 * Receives a notification from the system to start detect of faces in a video.
 */
public class AsyncFaceResultListener extends BaseAsyncResultListener {

    public static final String SUCCESS_EVENT = "asyncRekognitionFaceSuccess";

    public static final String FAILURE_EVENT = "asyncRekognitionFaceFailure";

    @Override
    protected String getSuccessEventName() {
        return SUCCESS_EVENT;
    }

    @Override
    protected String getFailureEventName() {
        return FAILURE_EVENT;
    }

    @Override
    protected Collection<EnrichmentMetadata> getEnrichmentMetadata(String jobId, Map<String, Serializable> params) {
        GetFaceDetectionRequest request = new GetFaceDetectionRequest()
                .withJobId(jobId);

        RekognitionService rs = Framework.getService(RekognitionService.class);
        GetFaceDetectionResult result = rs.getClient().getFaceDetection(request);

        AIComponent service = Framework.getService(AIComponent.class);
        DetectFacesEnrichmentProvider es = (DetectFacesEnrichmentProvider) service.getEnrichmentProvider(ENRICHMENT_NAME);
        return es.processResults((BlobTextFromDocument) params.get("doc"), (String) params.get("key"), result);
    }
}
