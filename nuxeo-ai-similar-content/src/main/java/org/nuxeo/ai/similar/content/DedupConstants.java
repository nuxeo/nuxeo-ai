/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo
 */
package org.nuxeo.ai.similar.content;

/**
 * Deduplication constants.
 */
public class DedupConstants {

    public static final String DEDUPLICATION_FACET = "Deduplicable";

    public static final String NOT_DUPLICATE_TAG = "not_duplicate";

    public static final String CONF_LISTENER_ENABLE = "nuxeo.ai.similar.content.listener.enable";

    public static final String DEFAULT_CONFIGURATION = "dedup-default-config";

    public static final String CONF_DEDUPLICATION_CONFIGURATION = "nuxeo.ai.similar.content.configuration.id";

    public static final String SKIP_INDEX_FLAG_UPDATE = "skip.index.flag.update";

    public static final String SIMILAR_DOCUMENTS_FOUND_EVENT = "similarDocumentsFound";

    public static final String SIMILAR_DOCUMENT_IDS_PARAM = "similarIds";
}
