/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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

import java.util.List;
import java.util.Optional;
import org.nuxeo.ai.adapters.DatasetExport;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;

@Operation(id = ExportProgressOperation.ID)
public class ExportProgressOperation {

    public static final String ID = "AI.ExportProgress";

    @Context
    protected BulkService bulkService;

    @Context
    protected DatasetExportService exportService;

    @Context
    protected CoreSession session;

    @Param(name = "modelId")
    protected String modelId;

    @OperationMethod(asyncService = BulkService.class)
    public ExportProgressStatus run() {
        String user = session.getPrincipal().getActingUser();

        DocumentModel exportDoc = exportService.latestDatasetExportForModel(session, modelId);
        if (exportDoc == null) {
            return null;
        }

        DatasetExport export = exportDoc.getAdapter(DatasetExport.class);
        List<BulkStatus> statuses = bulkService.getStatuses(user);

        Optional<ExportProgressStatus> status = statuses.stream()
                                                        .filter(st -> st.getId().equals(export.getJobId()))
                                                        .map(ExportProgressStatus::new)
                                                        .findAny();

        return status.orElse(null);
    }
}
