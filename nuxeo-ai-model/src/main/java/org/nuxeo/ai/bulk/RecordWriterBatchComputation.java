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

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.nuxeo.ai.bulk.DataSetExportStatusComputation.updateExportStatusProcessed;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.lib.stream.computation.AbstractBatchComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;

/**
 * Writes records using a RecordWriter
 */
public class RecordWriterBatchComputation extends AbstractBatchComputation {
    private static final Log log = LogFactory.getLog(RecordWriterBatchComputation.class);

    public RecordWriterBatchComputation(String name) {
        super(name, 1, 1);
    }

    @Override
    public void batchProcess(ComputationContext context, String inputStream, List<Record> records) {
        Map<String, List<Record>> recordsByCommand = records.stream().collect(groupingBy(Record::getKey, toList()));
        RecordWriter writer = Framework.getService(AIComponent.class).getRecordWriter(metadata.name());
        if (writer == null) {
            throw new NuxeoException("Unknown record write specified: " + metadata.name());
        }
        recordsByCommand.forEach((commandId, recordsOfCommand) -> {
            long errors = 0;
            try {
                errors = writer.write(recordsOfCommand);
            } catch (IOException e) {
                throw new NuxeoException(
                        String.format("Failed to write the %s batch for %s.", metadata.name(), commandId), e);
            } finally {
                updateExportStatusProcessed(context, commandId, recordsOfCommand.size(), errors);
            }
        });
    }

    @Override
    public void batchFailure(ComputationContext context, String inputStream, List<Record> records) {
        String commandId = records.isEmpty() ? "" : records.get(0).getKey();
        log.warn(String.format("Batch failure \"%s\" batch of %s records with command id: %s.",
                               metadata.name(), records.size(), commandId));
    }

}
