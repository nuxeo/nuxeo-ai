/*
 * (C) Copyright 2026 Hyland (http://hyland.com/) and others.
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
 */
package org.nuxeo.ai.contentintelligence;

import org.hyland.sdk.cic.http.client.CICServiceException;
import org.hyland.sdk.cic.ke.object.EnrichmentResult;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * Calls Hyland Content Intelligence Knowledge Enrichment through the Hyland CIC Java SDK.
 *
 * @since 5.0.5
 */
public interface ContentIntelligenceService {

    /**
     * Uploads the blob, requests the enrichment and polls its result.
     *
     * @param configName the name of the {@code knowledgeEnrichment} contribution to use
     * @return the result, or an in-progress result ({@code PROCESSING}) when it is not ready within the configured
     *         polling budget
     * @throws NuxeoException if the configuration is unknown or incomplete, or if a call fails; when the service
     *             answered with an error, the cause chain contains the {@link CICServiceException} and its HTTP
     *             status
     */
    EnrichmentResult enrich(String configName, Blob blob, ContentIntelligenceRequest request);
}
