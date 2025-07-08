package org.nuxeo.ai.model;

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;

@Operation(id = "Test.AI.ExportInterrupt", category = "Test", label = "Test Interrupt AI Dataset Export")
public class TestDatasetExportInterruptOperation {

    public static BulkService testBulkService;

    @Context
    public BulkService bulkService;

    @Param(name = "commandId")
    public String commandId;

    @OperationMethod
    public boolean run() {
        BulkService serviceToUse = testBulkService != null ? testBulkService : bulkService;
        BulkStatus status = serviceToUse.abort(commandId);
        return BulkStatus.State.ABORTED.equals(status.getState());
    }
}
