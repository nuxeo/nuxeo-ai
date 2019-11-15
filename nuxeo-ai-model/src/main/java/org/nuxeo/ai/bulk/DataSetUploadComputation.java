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

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.nuxeo.ai.AIConstants.EXPORT_ACTION_NAME;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.model.export.DatasetExportService;
import org.nuxeo.ecm.core.api.CloseableCoreSession;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.bulk.BulkCodecs;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Listens for the end of the Dataset export and uploads to the cloud.
 */
public class DataSetUploadComputation extends AbstractComputation {

    private static final Logger log = LogManager.getLogger(DataSetUploadComputation.class);

    public DataSetUploadComputation(String name) {
        super(name, 1, 0);
    }

    @Override
    public void processRecord(ComputationContext context, String inputStreamName, Record record) {
        BulkStatus status = BulkCodecs.getStatusCodec().decode(record.getData());
        log.debug("Processing record id: {}; with action name: {}", status.getId(), status.getAction());
        if (EXPORT_ACTION_NAME.equals(status.getAction())
                && BulkStatus.State.COMPLETED.equals(status.getState())) {
            BulkCommand cmd = Framework.getService(BulkService.class).getCommand(status.getId());
            if (cmd != null) {
                TransactionHelper.runInTransaction(() -> {
                            log.debug("Opening a session with Repository {} and User {}", cmd.getRepository(), cmd.getUsername());
                            try (CloseableCoreSession session =
                                         CoreInstance.openCoreSessionSystem(cmd.getRepository(), cmd.getUsername())) {
                                DocumentModel document = Framework.getService(DatasetExportService.class)
                                        .getDatasetExportDocument(session, cmd.getId());
                                if (document != null) {
                                    CloudClient client = Framework.getService(CloudClient.class);
                                    if (client.isAvailable()) {
                                        log.info("Uploading dataset to cloud for command {}," +
                                                " dataset doc {}", cmd.getId(), document.getId());

                                        String uid = client.uploadedDataset(document);
                                        log.info("Upload of dataset to cloud for command {} {}.", cmd.getId(),
                                                isNotEmpty(uid) ? "successful" : "failed");

                                        boolean success = client.addDatasetToModel(document, uid);
                                        log.info("Added dataset to AI_Model {} for command {} {}.", uid, cmd.getId(),
                                                success ? "successful" : "failed");
                                    } else {
                                        log.warn("Upload to cloud not possible for export command {}," +
                                                        " dataset doc {} and client {}",
                                                cmd.getId(), document.getId(), client.isAvailable());
                                        throw new NuxeoException("Upload to cloud not possible for export command "
                                                + cmd.getId() + ","
                                                + " dataset doc "
                                                + document.getId()
                                                + " and client " + client.isAvailable());
                                    }
                                } else {
                                    throw new NuxeoException("Unable to find DatasetExport with job id " + cmd.getId());
                                }
                            }

                            return null;
                        }
                );
            } else {
                log.warn("The bulk command with id {} is missing.  Unable to upload a dataset.", status.getId());
            }
        }
        context.askForCheckpoint();
    }
}
