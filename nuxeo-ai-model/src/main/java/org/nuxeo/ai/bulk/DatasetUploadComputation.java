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
        String commandId = export.getCommandId();
        BulkCommand command = Framework.getService(BulkService.class).getCommand(commandId);

        if (command != null) {
            runInTransaction(() -> {
                uploadDataset(export, commandId, command);
                return null;
            });
        } else {
            log.warn("The bulk command with id {} is missing.  Unable to upload a dataset.", commandId);
        }

        ctx.produceRecord(OUTPUT_1, record);
        ctx.askForCheckpoint();
    }

    protected void uploadDataset(ExportStatus export, String commandId, BulkCommand command) {
        CoreSession session = CoreInstance.getCoreSessionSystem(command.getRepository(), command.getUsername());
        DocumentModel document = Framework.getService(DatasetExportService.class)
                                          .getCorpusOfBatch(session, commandId, export.getId());

        if (document != null) {
            CloudClient client = Framework.getService(CloudClient.class);
            if (client.isAvailable()) {
                log.info("Uploading dataset to cloud for command {}," + " dataset doc {}", commandId, document.getId());

                // TODO: Attach corpus to corpora
                client.uploadedDataset(document);
            } else {
                log.error("Upload to cloud not possible for export command {}, dataset doc {} and client {}", commandId,
                        document.getId(), client.isAvailable());
            }
        } else {
            log.error("Unable to find DatasetExport with job id " + commandId);
        }
    }

}
