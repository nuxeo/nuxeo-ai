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
package org.nuxeo.ai.metadata;

/**
 * Provides constants for AI metadata
 */
public class MetadataConstants {

    //Enrichment
    public static final String ENRICHMENT_FACET = "Enrichable";
    public static final String ENRICHMENT_NAME = "enrichment";
    public static final String ENRICHMENT_PREFIX = "enrich:";
    public static final String ENRICHMENT_CLASSIFICATIONS = "classifications";
    public static final String AI_SERVICE_PROPERTY = "service";
    public static final String ENRICHMENT_TARGET_DOCPROP_PROPERTY = "targetDocumentProperties";
    public static final String NORMALIZED_PROPERTY = "normalized";
    public static final String ENRICHMENT_RAW_KEY_PROPERTY = "raw";
    public static final String AI_CREATOR_PROPERTY = "creator";
    public static final String ENRICHMENT_LABELS_PROPERTY = "labels";
    public static final String ENRICHMENT_KIND = "kind";
    
    private MetadataConstants() {
        // just Constants
    }
}
