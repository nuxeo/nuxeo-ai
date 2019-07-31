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

import static org.nuxeo.ai.bulk.DataSetBulkAction.TRAINING_COMPUTATION;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_DOCUMENTS_COUNT;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_EVALUATION_DATA;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_TRAINING_DATA;
import static org.nuxeo.ecm.core.bulk.BulkCodecs.DEFAULT_CODEC;
import static org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation.updateStatus;

import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.model.export.DatasetExportService;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CloseableCoreSession;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.lib.stream.codec.Codec;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.codec.CodecService;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Listens for the end of the Dataset export and raises an event.
 */
public class DataSetExportStatusComputation extends AbstractComputation {

    private static final Logger log = LogManager.getLogger(DataSetExportStatusComputation.class);

    protected final Set<String> writerNames;

    protected Map<String, Long> counters = new HashMap<>();

    public DataSetExportStatusComputation(String name, Set<String> writerNames) {
        super(name, 1, 1);
        this.writerNames = writerNames;
    }

    public static Codec<ExportBulkProcessed> getExportStatusCodec() {
        return Framework.getService(CodecService.class).getCodec(DEFAULT_CODEC, ExportBulkProcessed.class);
    }

    public static void updateExportStatusProcessed(ComputationContext context, String commandId, long processed, long errored) {
        ExportBulkProcessed exportStatus = new ExportBulkProcessed(commandId, processed, errored);
        context.produceRecord(OUTPUT_1, commandId, getExportStatusCodec().encode(exportStatus));
    }

    public static boolean isTraining(String name) {
        return TRAINING_COMPUTATION.equals(name);
    }

    @Override
    public void processRecord(ComputationContext context, String inputStreamName, Record record) {
        ExportBulkProcessed exportStatus = getExportStatusCodec().decode(record.getData());
        BulkService service = Framework.getService(BulkService.class);
        BulkStatus bulkStatus = service.getStatus(exportStatus.getCommandId());
        BulkStatus deltaStatus = BulkStatus.deltaOf(exportStatus.getCommandId());
        if (bulkStatus.getProcessingStartTime() == null) {
            deltaStatus.setProcessingStartTime(Instant.now());
        }
        if (isEndOfBatch(exportStatus, bulkStatus)) {
            BulkCommand command = service.getCommand(exportStatus.getCommandId());
            log.debug("Ending batch for {}", exportStatus.getCommandId());
            for (String name : writerNames) {
                RecordWriter writer = Framework.getService(AIComponent.class).getRecordWriter(name);
                if (writer == null) {
                    throw new NuxeoException(String.format("Unable to find record writer: %s", name));
                }
                if (writer.exists(exportStatus.getCommandId())) {
                    try {
                        Optional<Blob> blob = writer.complete(exportStatus.getCommandId());
                        blob.ifPresent(theBlob -> {

                            if (command != null) {
                                updateDatasetDocument(exportStatus, command, theBlob, isTraining(name));
                            } else {
                                log.warn(String.format(
                                        "The bulk command with id %s is missing.  Unable to save blob info for %s %s.",
                                        exportStatus.getCommandId(), name, theBlob.getDigest()));
                            }
                        });
                    } catch (IOException e) {
                        throw new NuxeoException(
                                String.format("Unable to complete action %s", exportStatus.getCommandId()), e);
                    }
                } else {
                    log.debug("No writer file exists for {} {}", exportStatus.getCommandId(), name);
                }
            }
            deltaStatus.setProcessingEndTime(Instant.now());
            // Clear counter
            counters.remove(exportStatus.getCommandId());
        }
        updateDelta(exportStatus.getCommandId(), exportStatus.getProcessed());
        deltaStatus.setErrorCount(exportStatus.getErrored());
        deltaStatus.setProcessed(exportStatus.getProcessed());
        updateStatus(context, deltaStatus);
        context.askForCheckpoint();
    }

    /**
     * Set the blob on the corpus document
     */
    protected void updateDatasetDocument(ExportBulkProcessed exportStatus, BulkCommand command, Blob theBlob, boolean isTraining) {
        TransactionHelper.runInTransaction(
                () -> {
                    try (CloseableCoreSession session =
                                 CoreInstance.openCoreSession(command.getRepository(), command.getUsername())) {
                        DocumentModel document = Framework.getService(DatasetExportService.class)
                                                          .getDatasetExportDocument(session, command.getId());
                        if (document != null) {
                            document.setPropertyValue(DATASET_EXPORT_DOCUMENTS_COUNT,
                                                      exportStatus.getProcessed() +
                                                              getCount(exportStatus.getCommandId()));
                            document.setPropertyValue(isTraining ? DATASET_EXPORT_TRAINING_DATA : DATASET_EXPORT_EVALUATION_DATA,
                                                      (Serializable) theBlob);
                            session.saveDocument(document);
                        } else {
                            log.warn(String.format("Unable to save blob %s for command id %s.",
                                                   theBlob.getDigest(), exportStatus.getCommandId()));
                        }
                    }
                }
        );
    }

    protected Long getCount(String commandId) {
        return counters.get(commandId);
    }

    protected void updateDelta(String commandId, long processed) {
        counters.computeIfPresent(commandId, (s, aLong) -> processed + aLong);
    }

    protected boolean isEndOfBatch(ExportBulkProcessed exportStatus, BulkStatus status) {
        Long processed = getCount(exportStatus.getCommandId());
        if (processed == null) {
            processed = 0L;
            counters.put(exportStatus.getCommandId(), processed);
        }
        log.debug("Checking end of batch for {}, count processed {}, export processed {} and total of {}",
                  exportStatus.getCommandId(), processed, exportStatus.getProcessed(), status.getTotal());
        return processed + exportStatus.getProcessed() >= status.getTotal();
    }
}
