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

import static org.nuxeo.ecm.core.bulk.BulkCodecs.DEFAULT_CODEC;
import static org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation.updateStatusProcessed;

import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventProducer;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.lib.stream.codec.Codec;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.codec.CodecService;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Listens for the end of the Dataset export and raises an event.
 */
public class DataSetExportStatusComputation extends AbstractComputation {

    public static final String DATASET_EXPORT_DONE_EVENT = "DATASET_EXPORT_DONE_EVENT";

    public static final String ACTION_ID = "ACTION_ID";

    public static final String ACTION_DATA = "ACTION_DATA";

    public static final String ACTION_BLOB_REF = "ACTION_BLOB_REF";

    public static final String ACTION_USERNAME = "ACTION_USER";

    protected final Set<String> writerNames;

    protected Map<String, Long> counters = new HashMap<>();

    public DataSetExportStatusComputation(String name, Set<String> writerNames) {
        super(name, 1, 1);
        this.writerNames = writerNames;
    }

    public static Codec<ExportBulkProcessed> getExportStatusCodec() {
        return Framework.getService(CodecService.class).getCodec(DEFAULT_CODEC, ExportBulkProcessed.class);
    }

    public static void updateExportStatusProcessed(ComputationContext context, String commandId, long processed) {
        ExportBulkProcessed exportStatus = new ExportBulkProcessed(commandId, processed);
        context.produceRecord(OUTPUT_1, commandId, getExportStatusCodec().encode(exportStatus));
    }

    @Override
    public void processRecord(ComputationContext context, String inputStreamName, Record record) {
        ExportBulkProcessed exportStatus = getExportStatusCodec().decode(record.getData());
        BulkService service = Framework.getService(BulkService.class);
        if (isEndOfBatch(exportStatus)) {
            for (String name : writerNames) {
                RecordWriter writer = Framework.getService(AIComponent.class).getRecordWriter(name);
                if (writer == null) {
                    throw new NuxeoException(String.format("Unable to find record writer: %s", name));
                }
                if (writer.exists(exportStatus.getCommandId())) {
                    try {
                        Optional<String> blob = writer.complete(exportStatus.getCommandId());
                        blob.ifPresent(blobRef -> {

                            BulkCommand command = service.getCommand(exportStatus.getCommandId());
                            // Raise an event
                            EventContextImpl eCtx = new EventContextImpl();
                            eCtx.setProperty(ACTION_ID, exportStatus.getCommandId());
                            eCtx.setProperty(ACTION_DATA, name);
                            eCtx.setProperty(ACTION_BLOB_REF, blobRef);
                            eCtx.setProperty(ACTION_USERNAME, command.getUsername());
                            eCtx.setRepositoryName(command.getRepository());
                            Event event = eCtx.newEvent(DATASET_EXPORT_DONE_EVENT);
                            Framework.getService(EventProducer.class).fireEvent(event);
                        });
                    } catch (IOException e) {
                        throw new NuxeoException(
                                String.format("Unable to complete action %s", exportStatus.getCommandId()), e);
                    }
                }
            }
            // Clear counter
            counters.remove(exportStatus.getCommandId());
        }
        updateDelta(exportStatus.getCommandId(), exportStatus.getProcessed());
        updateStatusProcessed(context, exportStatus.getCommandId(), exportStatus.getProcessed());
        context.askForCheckpoint();
    }

    protected Long getCount(String commandId) {
        return counters.get(commandId);
    }

    protected void updateDelta(String commandId, long processed) {
        Long currentCount = counters.get(commandId);
        if (currentCount != null) {
            counters.put(commandId, processed + currentCount);
        }
    }

    protected boolean isEndOfBatch(ExportBulkProcessed exportStatus) {
        BulkStatus status = Framework.getService(BulkService.class).getStatus(exportStatus.getCommandId());
        Long processed = getCount(exportStatus.getCommandId());
        if (processed == null) {
            processed = 0L;
            counters.put(exportStatus.getCommandId(), processed);
        }
        return processed + exportStatus.getProcessed() >= status.getCount();
    }
}
