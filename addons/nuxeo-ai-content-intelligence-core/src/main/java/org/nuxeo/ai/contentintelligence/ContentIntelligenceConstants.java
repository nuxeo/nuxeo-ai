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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Shared constants for the Content Intelligence enrichment module. Centralises identifiers and keys that are
 * referenced across {@link ContentIntelligenceEnrichmentProvider}, {@link ContentIntelligenceDescriptionListener},
 * and {@link StoreContentIntelligenceMetadata}.
 */
public final class ContentIntelligenceConstants {

    /** Registered name of the image-pipeline enrichment provider. */
    public static final String IMAGE_PROVIDER_NAME = "ai.contentintelligence";

    /** Registered name of the document-pipeline enrichment provider. */
    public static final String DOCUMENTS_PROVIDER_NAME = "ai.contentintelligence.documents";

    /** Nuxeo document property used to persist the AI-generated description. */
    public static final String DESCRIPTION_PROPERTY = "dc:description";

    /**
     * Hyland CI action keys whose result is a long-form description / summary. These never become tags; they are
     * persisted to {@code dc:description} by {@link ContentIntelligenceDescriptionListener} via the raw blob, and
     * are deliberately omitted from the label suggestion list so that any downstream tag-writer
     * (e.g. {@code StoreLabelsAsTags} when {@code nuxeo.enrichment.save.tags=true}) cannot turn the description
     * paragraph into a giant tag.
     * <p>
     * Order matters: when an entry carries both keys (extremely unlikely but possible for mixed-pipeline blobs),
     * {@code imageDescription} wins.
     */
    public static final Set<String> DESCRIPTION_ACTION_KEYS;

    static {
        Set<String> keys = new LinkedHashSet<>();
        keys.add("imageDescription");
        keys.add("textSummary");
        DESCRIPTION_ACTION_KEYS = Collections.unmodifiableSet(keys);
    }

    private ContentIntelligenceConstants() {
    }
}
