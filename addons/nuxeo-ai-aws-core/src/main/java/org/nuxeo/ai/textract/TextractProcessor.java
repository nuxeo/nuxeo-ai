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
import com.amazonaws.services.textract.model.Block;
import com.amazonaws.services.textract.model.BoundingBox;

/**
 * A processor of a Textract Response
 * @since 2.1.2
 */
public interface TextractProcessor {

    /**
     * Process Textract blocks.
     * You can optionally call addTag() or addLabel() to add to the normalized AI metadata.
     */
    void process(List<Block> blocks, CoreSession session, DocumentRef docRef, EnrichmentMetadata.Builder builder);

    /*
     * Turn a Block geometry into a normalized AIMetadata.Box
     */
    default AIMetadata.Box asBox(Block block) {
        BoundingBox box = block.getGeometry().getBoundingBox();
        return new AIMetadata.Box(box.getWidth(), box.getHeight(), box.getLeft(), box.getTop());
    }

    /**
     * Gets the normalized confidence from the block
     */
    default float normalizeConfidence(Block block) {
        return block.getConfidence() / 100;
    }

}
