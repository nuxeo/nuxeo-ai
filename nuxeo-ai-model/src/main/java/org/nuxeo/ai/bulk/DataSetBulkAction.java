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
import static org.apache.commons.lang3.StringUtils.split;
import static org.nuxeo.ai.AIConstants.EXPORT_ACTION_NAME;
import static org.nuxeo.ai.AIConstants.EXPORT_FEATURES_PARAM;
import static org.nuxeo.ai.AIConstants.EXPORT_SPLIT_PARAM;
import static org.nuxeo.ecm.core.bulk.StreamBulkProcessor.COUNTER_ACTION_NAME;
import static org.nuxeo.ecm.core.bulk.StreamBulkProcessor.KVWRITER_ACTION_NAME;
import static org.nuxeo.runtime.stream.pipes.functions.PropertyUtils.getPropertyValue;
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.toRecord;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.bulk.BulkCodecs;
import org.nuxeo.ecm.core.bulk.BulkCounter;
import org.nuxeo.ecm.core.bulk.actions.AbstractBulkAction;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.ComputationPolicy;
import org.nuxeo.lib.stream.computation.ComputationPolicyBuilder;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;
import java.io.Serializable;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bulk export data from Nuxeo to TFRecord
 * Split the dataset in training and validation sets.
 */
public class DataSetBulkAction extends AbstractBulkAction {

    public static final String TRAINING_ID = "o1";

    public static final String TRAINING_COMPUTATION_NAME = "training";

    public static final String VALIDATION_ID = "o2";

    public static final String VALIDATION_COMPUTATION_NAME = "validation";

    @Override
    public Topology getTopology(Map<String, String> options) {
        int size = getOptionAsInteger(options, BATCH_SIZE_OPT, DEFAULT_BATCH_SIZE);
        int timer = getOptionAsInteger(options, BATCH_THRESHOLD_MS_OPT, DEFAULT_BATCH_THRESHOLD_MS);

        int trainingBatchSize = getOptionAsInteger(options, TRAINING_COMPUTATION_NAME + "_" + BATCH_SIZE_OPT, DEFAULT_BATCH_SIZE);
        int trainingBatchTime = getOptionAsInteger(options, TRAINING_COMPUTATION_NAME + "_" + BATCH_THRESHOLD_MS_OPT, DEFAULT_BATCH_THRESHOLD_MS);
        int validationBatchSize = getOptionAsInteger(options, VALIDATION_COMPUTATION_NAME + "_" + BATCH_SIZE_OPT, DEFAULT_BATCH_SIZE);
        int validationBatchTime = getOptionAsInteger(options, VALIDATION_COMPUTATION_NAME + "_" + BATCH_THRESHOLD_MS_OPT, DEFAULT_BATCH_THRESHOLD_MS);

        ComputationPolicy trainingPolicy = new ComputationPolicyBuilder()
                .batchPolicy(trainingBatchSize, Duration.ofMillis(trainingBatchTime))
                .retryPolicy(ComputationPolicy.NO_RETRY)
                .continueOnFailure(true)
                .build();
        ComputationPolicy validationPolicy = new ComputationPolicyBuilder()
                .batchPolicy(validationBatchSize, Duration.ofMillis(validationBatchTime))
                .retryPolicy(ComputationPolicy.NO_RETRY)
                .continueOnFailure(true)
                .build();

        // Create a topology with ExportingComputation writing to either a training RecordWriterBatchComputation or a
        // validation RecordWriterBatchComputation.
        // The RecordWriterBatchComputation updates the counter action to indicate progress.
        // DataSetExportDoneComputation listen for the end
        Topology.Builder builder = Topology.builder();
        addComputations(builder, size, timer);
        return builder
                .addComputation(() -> new RecordWriterBatchComputation(TRAINING_COMPUTATION_NAME, 1, 1, trainingPolicy),
                                asList("i1:" + TRAINING_COMPUTATION_NAME, "o1:" + COUNTER_ACTION_NAME))
                .addComputation(() -> new RecordWriterBatchComputation(VALIDATION_COMPUTATION_NAME, 1, 1, validationPolicy),
                                asList("i1:" + VALIDATION_COMPUTATION_NAME, "o1:" + COUNTER_ACTION_NAME))
                .addComputation(() -> new DataSetExportDoneComputation("end_training_validation",
                                                                       new HashSet<>(asList(TRAINING_COMPUTATION_NAME, VALIDATION_COMPUTATION_NAME))),
                                asList("i1:" + KVWRITER_ACTION_NAME))
                .build();
    }

    @Override
    protected Topology.Builder addComputations(Topology.Builder builder, int size, int threshold) {
        return builder.addComputation(() -> new ExportingComputation(EXPORT_ACTION_NAME, size, threshold),
                                      asList("i1:" + EXPORT_ACTION_NAME,
                                             TRAINING_ID + ":" + TRAINING_COMPUTATION_NAME,
                                             VALIDATION_ID + ":" + VALIDATION_COMPUTATION_NAME,
                                             "o3:" + COUNTER_ACTION_NAME));
    }

    /**
     * Export the dataset and randomly split it into 2 groups, training and validation.
     */
    public static class ExportingComputation extends AbstractBulkComputation {

        public static final int DEFAULT_SPLIT = 75;

        ComputationContext context;

        public ExportingComputation(String name, int batchSize, int batchThresholdMs) {
            super(name, 1, 3, batchSize, batchThresholdMs);
        }

        @Override
        public void init(ComputationContext context) {
            super.init(context);
            this.context = context;
        }

        @Override
        public void produceOutput(ComputationContext context) {
            // The parent produces a counter, we don't want to do that.
        }

        @Override
        protected void compute(CoreSession coreSession, List<String> ids, Map<String, Serializable> properties) {
            if (ids == null || ids.isEmpty()) {
                return;
            }

            List<String> customProperties = asList(split((String) properties.get(EXPORT_FEATURES_PARAM), ","));
            int percentSplit = Integer.parseInt((String) properties.getOrDefault(EXPORT_SPLIT_PARAM, DEFAULT_SPLIT));
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int discarded = 0;
            for (String id : ids) {

                try {
                    DocumentModel doc = coreSession.getDocument(new IdRef(id));
                    BlobTextStream subDoc = docSerialize(doc, customProperties);
                    boolean isTraining = random.nextInt(1, 101) <= percentSplit;
                    if (subDoc != null) {
                        getLog().debug(isTraining + " " + subDoc);
                        Record record = toRecord(currentCommandId, subDoc);
                        context.produceRecord(isTraining ? TRAINING_ID : VALIDATION_ID, record);
                    } else {
                        discarded++;
                    }
                } catch (DocumentNotFoundException e) {
                    getLog().error("DocumentNotFoundException: " + id);
                    discarded++;
                }

            }
            if (discarded > 0) {
                BulkCounter counter = new BulkCounter(currentCommandId, discarded);
                context.produceRecord("o3", currentCommandId, BulkCodecs.getBulkCounterCodec().encode(counter));
            }
            context.askForCheckpoint();
            getLog().debug("There  were Ids " + ids.size());
        }

        /**
         * Serialize the properties to the BlobTextStream format.
         */
        protected BlobTextStream docSerialize(DocumentModel doc, List<String> propertiesList) {
            BlobTextStream blobTextStream = new BlobTextStream(doc);
            Map<String, String> properties = blobTextStream.getProperties();

            propertiesList.forEach(propName -> {
                Serializable propVal = getPropertyValue(doc, propName);
                if (propVal instanceof ManagedBlob) {
                    // Currently only 1 blob property is supported
                    blobTextStream.addXPath(propName);
                    blobTextStream.setBlob((ManagedBlob) propVal);
                } else if (propVal != null) {
                    properties.put(propName, propVal.toString());
                }
            });

            int blobs = blobTextStream.getBlob() != null ? 1 : 0;
            if (properties.size() + blobs == propertiesList.size()) {
                return blobTextStream;
            } else {
                getLog().debug(String.format("Document %s one of the following properties is null so skipping. %s",
                                             doc.getId(), propertiesList));
                return null;
            }

        }

    }
}
