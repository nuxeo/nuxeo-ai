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
package org.nuxeo.ai.bulk;

import static java.util.Arrays.asList;
import static java.util.Collections.shuffle;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.split;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.AIConstants.EXPORT_ACTION_NAME;
import static org.nuxeo.ai.AIConstants.EXPORT_FEATURES_PARAM;
import static org.nuxeo.ai.AIConstants.EXPORT_SPLIT_PARAM;
import static org.nuxeo.ai.bulk.DataSetExportStatusComputation.updateExportStatusProcessed;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toRecord;
import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.DONE_STREAM;
import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.STATUS_STREAM;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_2;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_3;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.ListUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.metadata.SuggestionMetadataWrapper;
import org.nuxeo.ai.pipes.functions.PropertyUtils;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.stream.StreamProcessorTopology;

/**
 * Bulk export data from Nuxeo to TFRecord Split the dataset in training and validation sets.
 */
public class DataSetBulkAction implements StreamProcessorTopology {



    public static final String TRAINING_STREAM = "exp-training";

    public static final String TRAINING_COMPUTATION = "training";

    public static final String VALIDATION_STREAM = "exp-validation";

    public static final String VALIDATION_COMPUTATION = "validation";

    public static final String EXPORT_STATUS_STREAM = "exp-status";

    public static final String EXPORT_STATUS_COMPUTATION = "exp-status-comp";

    public static final String EXPORT_UPLOAD_COMPUTATION = "exp-upload-comp";

    /**
     * Create a topology with ExportingComputation writing to either a training RecordWriterBatchComputation or a
     * validation RecordWriterBatchComputation. DataSetExportStatusComputation listen for the end
     */
    @Override
    public Topology getTopology(Map<String, String> options) {
        return Topology.builder()
                       .addComputation(() -> new ExportingComputation(EXPORT_ACTION_NAME),
                               asList(INPUT_1 + ":" + EXPORT_ACTION_NAME, //
                                       OUTPUT_1 + ":" + EXPORT_STATUS_STREAM, //
                                       OUTPUT_2 + ":" + TRAINING_STREAM, //
                                       OUTPUT_3 + ":" + VALIDATION_STREAM))

                       .addComputation(() -> new RecordWriterBatchComputation(TRAINING_COMPUTATION),
                               asList(INPUT_1 + ":" + TRAINING_STREAM, //
                                       OUTPUT_1 + ":" + EXPORT_STATUS_STREAM))

                       .addComputation(() -> new RecordWriterBatchComputation(VALIDATION_COMPUTATION),
                               asList(INPUT_1 + ":" + VALIDATION_STREAM, //
                                       OUTPUT_1 + ":" + EXPORT_STATUS_STREAM))

                       .addComputation(
                               () -> new DataSetExportStatusComputation(EXPORT_STATUS_COMPUTATION,
                                       new HashSet<>(asList(TRAINING_COMPUTATION, VALIDATION_COMPUTATION))),
                               asList(INPUT_1 + ":" + EXPORT_STATUS_STREAM, //
                                       OUTPUT_1 + ":" + STATUS_STREAM))

                       .addComputation(() -> new DataSetUploadComputation(EXPORT_UPLOAD_COMPUTATION),
                               singletonList(INPUT_1 + ":" + DONE_STREAM))
                       .build();
    }

    /**
     * Export the dataset and randomly split it into 2 groups, training and validation.
     */
    public static class ExportingComputation extends AbstractBulkComputation {

        public static final int DEFAULT_SPLIT = 75;

        private static final Logger log = LogManager.getLogger(ExportingComputation.class);

        List<Record> training = new ArrayList<>();

        List<Record> validation = new ArrayList<>();

        int discarded;

        public ExportingComputation(String name) {
            super(name, 3);
        }

        @Override
        protected void compute(CoreSession coreSession, List<String> ids, Map<String, Serializable> properties) {
            String[] props = split((String) properties.get(EXPORT_FEATURES_PARAM), ",");
            String split = (String) properties.getOrDefault(EXPORT_SPLIT_PARAM, String.valueOf(DEFAULT_SPLIT));

            int percentSplit = Integer.parseInt(split);

            // Split documents list into training and validation datasets
            DocumentModelList docs = loadDocuments(coreSession, ids);
            shuffle(docs);
            List<List<DocumentModel>> datasets = ListUtils.partition(docs, (docs.size() - 1) * percentSplit / 100);

            for (DocumentModel doc : datasets.get(0)) {
                Record record = createRecordFromDoc(props, doc);
                if (record == null) {
                    log.error("Couldn't create record from document {}", doc.getId());
                    continue;
                }
                training.add(record);
            }
            for (DocumentModel doc : datasets.get(1)) {
                Record record = createRecordFromDoc(props, doc);
                if (record == null) {
                    log.error("Couldn't create record from document {}", doc.getId());
                    continue;
                }
                validation.add(record);
            }
            if (log.isDebugEnabled()) {
                log.debug("Training dataset size: {} - Validation dataset size: {} - Command {}", training.size(),
                        validation.size(), command.getId());
            }
        }

        protected Record createRecordFromDoc(String[] props, DocumentModel doc) {
            List<String> targetProperties = new ArrayList<>(asList(props));

            if (doc.hasFacet(ENRICHMENT_FACET)) {
                SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(doc);
                targetProperties.removeAll(wrapper.getAutoFilled());
                targetProperties.removeAll(wrapper.getAutoCorrected());
            }

            BlobTextFromDocument subDoc = null;
            if (!targetProperties.isEmpty()) {
                subDoc = PropertyUtils.docSerialize(doc, new HashSet<>(targetProperties));
            }

            if (subDoc != null) {
                return toRecord(command.getId(), subDoc);
            } else {
                discarded++;
                return null;
            }
        }

        @Override
        public void endBucket(ComputationContext context, BulkStatus ignored) {
            if (discarded > 0) {
                // Error records are for information only, they still need to be counted as "processed"
                updateExportStatusProcessed(context, command.getId(), discarded, discarded);
                discarded = 0;
            }
            training.forEach(record -> context.produceRecord(OUTPUT_2, record));
            training.clear();
            validation.forEach(record -> context.produceRecord(OUTPUT_3, record));
            validation.clear();
            context.askForCheckpoint();
        }

    }
}
