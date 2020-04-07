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
package org.nuxeo.ai.model.export;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_MODEL_END_DATE;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_MODEL_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_MODEL_NAME;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_MODEL_START_DATE;
import static org.nuxeo.ai.bulk.ExportInitComputation.DEFAULT_SPLIT;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.kv.KeyValueService;

@Operation(id = DatasetExportOperation.ID, category = Constants.CAT_SERVICES, label = "Bulk export a dataset", description = "Run a bulk export on a set of documents expressed by a NXQL query.")
public class DatasetExportOperation {

    private static final Logger log = LogManager.getLogger(DatasetExportOperation.class);

    public static final String ID = "AI.DatasetExport";

    public static final String EXPORT_KVS_STORE = "aiExportNames";

    public static final long TTL_128H = 128 * 60 * 60 * 1000;

    @Context
    protected DatasetExportService service;

    @Context
    protected KeyValueService kvs;

    @Context
    protected CoreSession session;

    @Param(name = "query")
    protected String query;

    @Param(name = "inputs")
    protected StringList inputs;

    @Param(name = "outputs")
    protected StringList outputs;

    @Param(name = "split", required = false)
    protected int split = DEFAULT_SPLIT;

    @Param(name = "model_id", required = false)
    protected String modelId;

    @Param(name = "model_name", required = false)
    protected String modelName;

    @Param(name = "model_start_date", required = false)
    protected Date start;

    @Param(name = "model_end_date", required = false)
    protected Date end;

    @OperationMethod
    public String run() {
        HashMap<String, Serializable> params = buildDatasetParameters();
        String exportId = service.export(session, query, inputs, outputs, split, params);
        if (isNotEmpty(exportId) && isNotEmpty(modelName)) {
            kvs.getKeyValueStore(EXPORT_KVS_STORE).put(exportId, modelName, TTL_128H);
        }

        return exportId;
    }

    protected HashMap<String, Serializable> buildDatasetParameters() {
        HashMap<String, Serializable> params = new HashMap<>();
        if (isNotEmpty(modelId)) {
            params.put(DATASET_EXPORT_MODEL_ID, modelId);
        }

        if (isNotEmpty(modelName)) {
            params.put(DATASET_EXPORT_MODEL_NAME, modelName);
        }

        if (start != null) {
            params.put(DATASET_EXPORT_MODEL_START_DATE, start);
        }

        if (end != null) {
            params.put(DATASET_EXPORT_MODEL_END_DATE, end);
        }

        return params;
    }
}
