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

import org.nuxeo.ai.aws.dto.DocumentAnalysisResult;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentRef;

/**
 * A processor of Textract blocks using domain DTOs (AWS SDK independent).
 *
 * @since 2.1.2
 */
public interface TextractProcessor<T> {

    /**
     * Process Textract blocks and return processed results. You can optionally call addTag() or addLabel() to add to
     * the normalized AI metadata.
     */
    T process(List<DocumentAnalysisResult.Block> blocks, CoreSession session, DocumentRef docRef,
            EnrichmentMetadata.Builder builder);

    /**
     * Turn a block geometry into a normalized AIMetadata.Box.
     */
    default AIMetadata.Box asBox(DocumentAnalysisResult.Block block) {
        DocumentAnalysisResult.BoundingBox bb = block.boundingBox();
        if (bb != null) {
            return new AIMetadata.Box(
                    bb.width() != null ? bb.width() : 0.0f,
                    bb.height() != null ? bb.height() : 0.0f,
                    bb.left() != null ? bb.left() : 0.0f,
                    bb.top() != null ? bb.top() : 0.0f);
        }
        return new AIMetadata.Box(0.0f, 0.0f, 0.0f, 0.0f);
    }

    /**
     * Gets the normalized confidence from the block.
     */
    default float normalizeConfidence(DocumentAnalysisResult.Block block) {
        Float confidence = block.confidence();
        return confidence != null ? confidence / 100 : 0.0f;
    }
}
