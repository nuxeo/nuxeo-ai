/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.textract;

import java.util.List;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentRef;

/**
 * A generic processor of Textract blocks - AWS SDK independent
 *
 * @since 2.1.2
 */
public interface TextractProcessor<T> {

    /**
     * Process Textract blocks and return processed results.
     * You can optionally call addTag() or addLabel() to add to the normalized AI metadata.
     */
    T process(List<Object> blocks, CoreSession session, DocumentRef docRef, EnrichmentMetadata.Builder builder);

    /*
     * Turn a block geometry into a normalized AIMetadata.Box
     * Default implementation for backward compatibility
     */
    default AIMetadata.Box asBox(Object block) {
        // Default implementation - subclasses should override if needed
        return new AIMetadata.Box(0.0f, 0.0f, 0.0f, 0.0f);
    }

    /**
     * Gets the normalized confidence from the block
     * Default implementation for backward compatibility
     */
    default float normalizeConfidence(Object block) {
        // Default implementation - subclasses should override if needed
        return 0.0f;
    }
}
