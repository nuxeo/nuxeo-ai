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
import static org.nuxeo.ai.bulk.ExportHelper.runInTransaction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.model.export.DatasetExportService;
import org.nuxeo.ai.pipes.types.ExportStatus;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;

/**
 * Listens for the end of the Dataset export and uploads to the cloud.
 */
public class DatasetUploadComputation extends AbstractComputation {

    private static final Logger log = LogManager.getLogger(DatasetUploadComputation.class);

    public DatasetUploadComputation(String name) {
        super(name, 1, 1);
    }

    @Override
    public void processRecord(ComputationContext ctx, String inputStreamName, Record record) {
        ExportStatus export = getAvroCodec(ExportStatus.class).decode(record.getData());
        BulkCommand command = Framework.getService(BulkService.class).getCommand(export.getCommandId());
        if (command != null) {
            runInTransaction(() -> {
                uploadDataset(export, command);
                return null;
            });
        } else {
            log.warn("The bulk command with id {} is missing.  Unable to upload a dataset.", export.getCommandId());
        }

        // use ExportStatus id (batch id) as the record id to ensure same node execution
        ctx.produceRecord(OUTPUT_1, export.getId(), record.getData());
        ctx.askForCheckpoint();
    }

    protected void uploadDataset(ExportStatus status, BulkCommand command) {
        CoreSession session = CoreInstance.getCoreSessionSystem(command.getRepository(), command.getUsername());
        DatasetExportService service = Framework.getService(DatasetExportService.class);

        String commandId = command.getId();
        DocumentModel document = service.getCorpusOfBatch(session, commandId, status.getId());

        long processed = status.getProcessed();
        long errored = status.getErrored();
        if (processed - errored <= 0) {
            log.warn("{} documents were processed with {} errors for command {}, dataset doc: {}; skipping upload",
                    processed, errored, commandId, document);
        } else if (document != null) {
            // Trying to upload dataset.
            CloudClient client = Framework.getService(CloudClient.class);
            if (client.isAvailable(session)) {
                log.info("Uploading dataset to cloud for command {}, dataset doc {} processed {} documents, {} errors",
                        commandId, document.getId(), processed, errored);
                if (client.uploadDataset(document) == null) {
                    log.warn("Document wasn't uploaded {}", document.getId());
                }
            } else {
                log.warn("Upload to cloud not possible for export command {}, export {} and client {}", commandId,
                        status.getId(), client.isAvailable(session));
            }
        } else {
            log.error("Unable to find DatasetExport with job id " + commandId);
        }
    }
}