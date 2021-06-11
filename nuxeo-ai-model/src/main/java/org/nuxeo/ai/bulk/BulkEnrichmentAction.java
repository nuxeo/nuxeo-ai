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
package org.nuxeo.ai.bulk;

import static org.nuxeo.ai.pipes.services.JacksonUtil.toRecord;
import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.STATUS_STREAM;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_2;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.model.ModelProperty;
import org.nuxeo.ai.model.serving.ModelServingService;
import org.nuxeo.ai.pipes.functions.PropertyUtils;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.sdk.objects.PropertyType;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.StreamProcessorTopology;

/**
 * A BAF action to add enrichment or suggestions to a number of documents. It does this by sending a sub-set of the
 * document properties to a stream for downstream processing. The current implementation only works with custom models.
 */
public class BulkEnrichmentAction implements StreamProcessorTopology {

    public static final String ACTION_NAME = "bulkEnrich";

    public static final String INPUT_STREAM = "ai/" + ACTION_NAME;

    public static final String COMPUTATION_NAME = INPUT_STREAM;

    public static final String STREAM_NAME = "streamName";

    @Override
    public Topology getTopology(Map<String, String> options) {
        String outputStream = options.get(STREAM_NAME);
        return Topology.builder()
                       .addComputation(DocEnrichingComputation::new,
                               Arrays.asList(INPUT_1 + ":" + INPUT_STREAM, OUTPUT_1 + ":" + STATUS_STREAM,
                                       OUTPUT_2 + ":" + outputStream))
                       .build();
    }

    public static class DocEnrichingComputation extends AbstractBulkComputation {

        private static final Logger log = LogManager.getLogger(DocEnrichingComputation.class);

        List<Record> outputs = new ArrayList<>();

        public DocEnrichingComputation() {
            super(COMPUTATION_NAME, 2);
        }

        @Override
        protected void compute(CoreSession coreSession, List<String> ids, Map<String, Serializable> options) {
            ModelServingService modelServingService = Framework.getService(ModelServingService.class);
            for (DocumentModel doc : loadDocuments(coreSession, ids)) {
                Set<ModelProperty> inputs = modelServingService.getInputs(doc);
                if (!inputs.isEmpty()) {
                    Set<PropertyType> inputPairs = inputs.stream()
                                                         .map(p -> PropertyType.of(p.getName(), p.getType()))
                                                         .collect(Collectors.toSet());
                    BlobTextFromDocument blobTextFromDocument = PropertyUtils.docSerialize(doc, inputPairs);
                    outputs.add(toRecord(blobTextFromDocument.getKey(), blobTextFromDocument));
                }
            }
        }

        @Override
        public void endBucket(ComputationContext context, BulkStatus delta) {
            outputs.forEach(record -> context.produceRecord(OUTPUT_2, record));
            outputs.clear();
            updateStatus(context, delta);
            context.askForCheckpoint();
        }
    }
}
