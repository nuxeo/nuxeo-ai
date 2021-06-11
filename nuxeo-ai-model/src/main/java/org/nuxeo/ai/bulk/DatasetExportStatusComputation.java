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

import static org.nuxeo.ai.bulk.ExportHelper.getAvroCodec;
import static org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation.updateStatus;

import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.pipes.types.ExportStatus;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;

/**
 * Listens for the end of the Dataset export and raises an event.
 */
public class DatasetExportStatusComputation extends AbstractComputation {

    private static final Logger log = LogManager.getLogger(DatasetExportStatusComputation.class);

    public DatasetExportStatusComputation(String name) {
        super(name, 1, 1);
    }

    @Override
    public void processRecord(ComputationContext context, String inputStreamName, Record record) {
        ExportStatus export = getAvroCodec(ExportStatus.class).decode(record.getData());

        String commandId = export.getCommandId();

        BulkService service = Framework.getService(BulkService.class);
        BulkStatus bulkStatus = service.getStatus(commandId);
        BulkStatus deltaStatus = BulkStatus.deltaOf(commandId);
        if (bulkStatus.getProcessingStartTime() == null) {
            deltaStatus.setProcessingStartTime(Instant.now());
        }

        log.info("Received status {} batch id {} processed: {} errored: {}", export.getCommandId(), export.getId(),
                export.getProcessed(), export.getErrored());

        deltaStatus.setProcessingEndTime(Instant.now());
        deltaStatus.setErrorCount(export.getErrored());
        deltaStatus.setProcessed(export.getProcessed());
        updateStatus(context, deltaStatus);

        context.askForCheckpoint();
    }

}
