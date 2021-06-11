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

import static org.nuxeo.ai.enrichment.LabelsEnrichmentProvider.MINIMUM_CONFIDENCE;

import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.pipes.streams.Initializable;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentRef;
import com.amazonaws.services.textract.model.Block;

/**
 * Debugs the blocks
 */
public class DebuggingTextractProcessor implements TextractProcessor, Initializable {

    public static final String DEFAULT_CONFIDENCE = "70";

    private static final Logger log = LogManager.getLogger(DebuggingTextractProcessor.class);

    protected float minConfidence;

    @Override
    public void init(Map<String, String> options) {
        minConfidence = Float.parseFloat(options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
    }

    @Override
    public void process(List<Block> blocks, CoreSession session, DocumentRef docRef,
            EnrichmentMetadata.Builder builder) {
        blocks.forEach(block -> {
            if (log.isDebugEnabled()) {
                log.debug(AWSHelper.getInstance().debugTextractBlock(block));
            }
        });
    }

}
