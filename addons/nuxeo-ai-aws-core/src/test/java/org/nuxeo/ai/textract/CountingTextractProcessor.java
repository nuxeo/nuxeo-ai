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

import static java.util.Collections.singletonList;

import java.util.List;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentRef;
import com.amazonaws.services.textract.model.Block;

public class CountingTextractProcessor implements TextractProcessor {

    @Override
    public void process(List<Block> blocks, CoreSession session, DocumentRef docRef,
            EnrichmentMetadata.Builder builder) {
        builder.withLabels(singletonList(new LabelSuggestion("countingProp",
                singletonList(new AIMetadata.Label(String.format("There are %s blocks.", blocks.size()), 1)))));
    }
}
