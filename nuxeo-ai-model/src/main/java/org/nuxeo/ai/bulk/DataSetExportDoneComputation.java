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

import static org.nuxeo.ecm.core.bulk.BulkComponent.BULK_KV_STORE_NAME;
import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.COMMAND;

import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.bulk.BulkCodecs;
import org.nuxeo.ecm.core.bulk.BulkCommand;
import org.nuxeo.ecm.core.bulk.BulkStatus;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventProducer;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Listens for the end of the Dataset export and raises an event.
 */
public class DataSetExportDoneComputation extends AbstractComputation {

    public static final String DATASET_EXPORT_DONE_EVENT = "DATASET_EXPORT_DONE_EVENT";

    public static final String ACTION_ID = "ACTION_ID";

    public static final String ACTION_DATA = "ACTION_DATA";

    public static final String ACTION_BLOB_REF = "ACTION_BLOB_REF";

    public static final String ACTION_USERNAME = "ACTION_USER";

    protected final Set<String> writerNames;

    public DataSetExportDoneComputation(String name, Set<String> writerNames) {
        super(name, 1, 0);
        this.writerNames = writerNames;
    }

    @Override
    public void processRecord(ComputationContext context, String inputStreamName, Record record) {
        BulkStatus status = BulkCodecs.getBulkStatusCodec().decode(record.getData());
        if (isCompleted(status)) {
            for (String name : writerNames) {
                RecordWriter writer = Framework.getService(AIComponent.class).getRecordWriter(name);
                if (writer.exists(status.getId())) {
                    try {
                        Optional<String> blob = writer.complete(status.getId());
                        blob.ifPresent(blobRef -> {

                            KeyValueStore kvStore = Framework.getService(KeyValueService.class)
                                                             .getKeyValueStore(BULK_KV_STORE_NAME);
                            BulkCommand command = BulkCodecs.getBulkCommandCodec()
                                                                   .decode(kvStore.get(status.getId() + COMMAND));
                            //Raise an event
                            EventContextImpl eCtx = new EventContextImpl();
                            eCtx.setProperty(ACTION_ID, status.getId());
                            eCtx.setProperty(ACTION_DATA, name);
                            eCtx.setProperty(ACTION_BLOB_REF, blobRef);
                            eCtx.setProperty(ACTION_USERNAME, command.getUsername());
                            eCtx.setRepositoryName(command.getRepository());
                            Event event = eCtx.newEvent(DATASET_EXPORT_DONE_EVENT);
                            Framework.getService(EventProducer.class).fireEvent(event);
                        });
                    } catch (IOException e) {
                        throw new NuxeoException(String.format("Unable to complete action %s", status.getId()), e);
                    }
                }
            }
        }
        context.askForCheckpoint();
    }

    protected boolean isCompleted(BulkStatus status) {
        return status.getProcessed() >= status.getCount();
    }
}
