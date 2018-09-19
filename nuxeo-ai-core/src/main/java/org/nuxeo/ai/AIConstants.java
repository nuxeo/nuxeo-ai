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
package org.nuxeo.ai;

/**
 * Provides constants for AI metadata
 */
public class AIConstants {

    public static final String AI_KIND_DIRECTORY = "aikind";

    //Enrichment
    public static final String ENRICHMENT_FACET = "Enrichable";
    public static final String ENRICHMENT_SCHEMA_NAME = "enrichment";
    public static final String ENRICHMENT_SCHEMA_PREFIX = "enrichment:";
    public static final String ENRICHMENT_ITEMS = "items";
    public static final String AI_SERVICE_PROPERTY = "service";
    public static final String ENRICHMENT_INPUT_DOCPROP_PROPERTY = "inputProperties";
    public static final String NORMALIZED_PROPERTY = "normalized";
    public static final String ENRICHMENT_RAW_KEY_PROPERTY = "raw";
    public static final String AI_CREATOR_PROPERTY = "creator";
    public static final String ENRICHMENT_LABELS_PROPERTY = "labels";
    public static final String ENRICHMENT_KIND_PROPERTY = "kind";


    public static final String IMAGE_TYPE = "img";
    public static final String TEXT_TYPE = "txt";

    private AIConstants() {
        // just Constants
    }
}
