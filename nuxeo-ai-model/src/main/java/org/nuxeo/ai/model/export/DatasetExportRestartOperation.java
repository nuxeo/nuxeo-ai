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

import static org.nuxeo.ecm.automation.core.Constants.CAT_SERVICES;

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;

@Operation(id = DatasetExportRestartOperation.ID,
        category = CAT_SERVICES,
        label = "Restart AI Dataset Export",
        description = "Restarts an aborted/completed dataset export")
public class DatasetExportRestartOperation {

    public static final String ID = "AI.ExportRestart";

    @Context
    protected BulkService bulkService;

    @Param(name = "commandId")
    protected String commandId;

    @OperationMethod
    public String run() {
        BulkCommand command = bulkService.getCommand(commandId);
        return bulkService.submit(command);
    }
}
