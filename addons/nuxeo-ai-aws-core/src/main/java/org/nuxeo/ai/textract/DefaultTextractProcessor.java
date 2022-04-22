/*
 * (C) Copyright 2006-2022 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 * Contributors:
 *    Andrei Nechaev
 *
 */
package org.nuxeo.ai.textract;

import static java.util.Collections.singletonList;
import static org.nuxeo.ai.enrichment.LabelsEnrichmentProvider.MINIMUM_CONFIDENCE;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ai.pipes.streams.Initializable;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentRef;
import com.amazonaws.services.textract.model.Block;

/**
 * Default Textract Processor
 */
public class DefaultTextractProcessor implements TextractProcessor, Initializable {

    public static final String DEFAULT_CONFIDENCE = "70";

    protected float minConfidence;

    @Override
    public void init(Map<String, String> options) {
        minConfidence = Float.parseFloat(options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
    }

    @Override
    public void process(List<Block> blocks, CoreSession session, DocumentRef docRef,
            EnrichmentMetadata.Builder builder) {
        List<LabelSuggestion> labels = blocks.stream()
                                             .filter(b -> b.getConfidence() != null
                                                     && b.getConfidence() > minConfidence)
                                             .map(b -> new LabelSuggestion("", singletonList(
                                                     new AIMetadata.Label(b.getText(), b.getConfidence()))))
                                             .collect(Collectors.toList());
        builder.withLabels(labels);
    }
}
