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

import static org.nuxeo.ai.bulk.DataSetExportStatusComputation.updateExportStatusProcessed;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.lib.stream.computation.AbstractBatchComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.ComputationPolicy;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;
import java.io.IOException;
import java.util.List;

/**
 * A batch computation that writes records using a RecordWriter
 */
public class RecordWriterBatchComputation extends AbstractBatchComputation {

    private static final Log log = LogFactory.getLog(RecordWriterBatchComputation.class);

    public RecordWriterBatchComputation(String name, int nbInputStreams, int nbOutputStreams, ComputationPolicy policy) {
        super(name, nbInputStreams, nbOutputStreams, policy);
    }

    @Override
    public void batchProcess(ComputationContext computationContext, String bulkId, List<Record> list) {
        RecordWriter writer = Framework.getService(AIComponent.class).getRecordWriter(metadata.name());
        if (writer == null) {
            throw new NuxeoException("Unknown record write specified: " + metadata.name());
        }
        try {
            writer.write(list);
            updateExportStatusProcessed(computationContext, currentInputStream, list.size());
        } catch (IOException e) {
            throw new NuxeoException(String.format("Failed to write the %s batch for %s.", metadata.name(), bulkId), e);
        }
    }

    @Override
    public void batchFailure(ComputationContext computationContext, String bulkId, List<Record> list) {
        log.warn(String.format("Batch failure %s batch for %s.", metadata.name(), bulkId));
    }

    @Override
    public void processRecord(ComputationContext context, String inputStreamName, Record record) {
        //Get the bulk command id from the record and use it as a key
        super.processRecord(context, record.getKey(), record);
    }
}
