/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.model.export;

import static org.nuxeo.ai.AIConstants.EXPORT_ACTION_NAME;
import static org.nuxeo.ai.model.export.DatasetExportOperation.EXPORT_KVS_STORE;
import static org.nuxeo.ecm.automation.core.Constants.CAT_SERVICES;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;

@Operation(id = DatasetExportUpdaterOperation.ID, category = CAT_SERVICES, label = "Retrieve AI Model", description = "Executes REST call to AI Services to get all AI models available")
public class DatasetExportUpdaterOperation {

    public static final String ID = "AI.ExportStatus";

    @Context
    protected BulkService bulkService;

    @Context
    protected KeyValueService kvsService;

    @Context
    protected CoreSession session;

    @OperationMethod(asyncService = BulkService.class)
    public List<ExportProgressStatus> run() {
        String user = session.getPrincipal().getActingUser();
        List<BulkStatus> statuses = bulkService.getStatuses(user);

        return statuses.stream()
                       .filter(status -> EXPORT_ACTION_NAME.equals(status.getAction()))
                       .sorted((o1, o2) -> o2.getSubmitTime().compareTo(o1.getSubmitTime()))
                       .limit(8)
                       .map(ExportProgressStatus::new)
                       .peek(this::updateName)
                       .collect(Collectors.toList());
    }

    protected void updateName(ExportProgressStatus status) {
        String modelName = getKVS().getString(status.getId());
        if (StringUtils.isNotEmpty(modelName)) {
            status.setName(modelName);
        }
    }

    protected KeyValueStore getKVS() {
        return kvsService.getKeyValueStore(EXPORT_KVS_STORE);
    }

}
