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

import static org.nuxeo.ai.enrichment.async.DetectCelebritiesEnrichmentProvider.ENRICHMENT_NAME;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.async.DetectCelebritiesEnrichmentProvider;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.runtime.api.Framework;

/**
 * Receives a notification from the system to start detect of celebrity faces in a video
 */
public class AsyncCelebritiesResultListener extends BaseAsyncResultListener {

    public static final String SUCCESS_EVENT = "asyncRekognitionCelebritiesSuccess";

    public static final String FAILURE_EVENT = "asyncRekognitionCelebritiesFailure";

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
        AIComponent service = Framework.getService(AIComponent.class);
        DetectCelebritiesEnrichmentProvider es = (DetectCelebritiesEnrichmentProvider) service.getEnrichmentProvider(
                ENRICHMENT_NAME);
        return es.processResults((BlobTextFromDocument) params.get("doc"), (String) params.get("key"), jobId);
    }
}
