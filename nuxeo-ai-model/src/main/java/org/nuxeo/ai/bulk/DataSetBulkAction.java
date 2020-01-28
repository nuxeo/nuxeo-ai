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
import static java.util.Collections.singletonList;
import static org.nuxeo.ai.AIConstants.EXPORT_ACTION_NAME;
import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.DONE_STREAM;
import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.STATUS_STREAM;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_2;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.stream.StreamProcessorTopology;

import com.google.common.collect.Sets;

/**
 * Bulk export data from Nuxeo to TFRecord Split the dataset in training and validation sets.
 */
public class DataSetBulkAction implements StreamProcessorTopology {

    private static final Logger log = LogManager.getLogger(DataSetBulkAction.class);

    public static final String TRAINING_STREAM = "exp-training";

    public static final String TRAINING_COMPUTATION = "training";

    public static final String VALIDATION_STREAM = "exp-validation";

    public static final String VALIDATION_COMPUTATION = "validation";

    public static final String DATASET_UPDATE_STREAM = "exp-dataset-update";

    public static final String DATASET_UPDATE_COMPUTATION = "dataset-update";

    public static final String EXPORT_STATUS_STREAM = "exp-status";

    public static final String EXPORT_STATUS_COMPUTATION = "exp-status-comp";

    public static final String EXPORT_UPLOAD_COMPUTATION = "upload-comp";

    public static final String EXPORT_UPLOAD_STREAM = "exp-upload-comp";

    public static final String EXPORT_DONE_COMPUTATION = "done-comp";

    /**
     * Create a topology with ExportingComputation writing to either a training RecordWriterBatchComputation or a
     * validation RecordWriterBatchComputation. DatasetExportStatusComputation listen for the end
     */
    @Override
    public Topology getTopology(Map<String, String> options) {
        return Topology.builder()
                .addComputation(() -> new ExportInitComputation(EXPORT_ACTION_NAME),
                        asList(INPUT_1 + ":" + EXPORT_ACTION_NAME, //
//                                OUTPUT_1 + ":" + EXPORT_STATUS_STREAM, //
                                OUTPUT_1 + ":" + TRAINING_STREAM, //
                                OUTPUT_2 + ":" + VALIDATION_STREAM))

                .addComputation(() -> new RecordWriterBatchComputation(TRAINING_COMPUTATION),
                        asList(INPUT_1 + ":" + TRAINING_STREAM, //
                                OUTPUT_1 + ":" + DATASET_UPDATE_STREAM))

                .addComputation(() -> new RecordWriterBatchComputation(VALIDATION_COMPUTATION),
                        asList(INPUT_1 + ":" + VALIDATION_STREAM, //
                                OUTPUT_1 + ":" + DATASET_UPDATE_STREAM))

                .addComputation(() -> new DatasetUpdateComputation(DATASET_UPDATE_COMPUTATION,
                                Sets.newHashSet(TRAINING_COMPUTATION, VALIDATION_COMPUTATION)),
                        asList(INPUT_1 + ":" + DATASET_UPDATE_STREAM, //
                                OUTPUT_1 + ":" + EXPORT_UPLOAD_STREAM))

                .addComputation(() -> new DatasetUploadComputation(EXPORT_UPLOAD_COMPUTATION),
                        asList(INPUT_1 + ":" + EXPORT_UPLOAD_STREAM,//
                                OUTPUT_1 + ":" + EXPORT_STATUS_STREAM))

                .addComputation(
                        () -> new DatasetExportStatusComputation(EXPORT_STATUS_COMPUTATION),
                        asList(INPUT_1 + ":" + EXPORT_STATUS_STREAM, //
                                OUTPUT_1 + ":" + STATUS_STREAM))

                .addComputation(() -> new ExportDoneComputation(EXPORT_DONE_COMPUTATION),
                        singletonList(INPUT_1 + ":" + DONE_STREAM))
                .build();
    }
}
