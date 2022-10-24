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
import static org.nuxeo.ai.bulk.ExportHelper.getAvroCodec;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.pipes.types.ExportRecord;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.lib.stream.codec.Codec;
import org.nuxeo.lib.stream.computation.AbstractBatchComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;

/**
 * Writes records using a RecordWriter
 */
public class RecordWriterBatchComputation extends AbstractBatchComputation {
    private static final Logger log = LogManager.getLogger(RecordWriterBatchComputation.class);

    public RecordWriterBatchComputation(String name) {
        super(name, 1, 1);
    }

    @Override
    public void batchProcess(ComputationContext context, String inputStream, List<Record> records) {
        RecordWriter writer = Framework.getService(AIComponent.class).getRecordWriter(metadata.name());
        if (writer == null) {
            throw new NuxeoException("Unknown record write specified: " + metadata.name());
        }

        Codec<ExportRecord> codec = getAvroCodec(ExportRecord.class);
        Map<String, List<ExportRecord>> grouped = records.stream()
                                                         .map(r -> codec.decode(r.getData()))
                                                         .collect(groupingBy(ExportRecord::getId, toList()));

        for (Map.Entry<String, List<ExportRecord>> entry : grouped.entrySet()) {
            List<ExportRecord> recs = entry.getValue();
            try {
                long errored = writer.write(recs);
                log.debug("Attempted to write {} records; Errors {}", recs.size(), errored);
            } catch (IOException e) {
                throw new NuxeoException("Failed to write batch " + metadata.name(), e);
            } finally {
                recs.forEach(rec -> {
                    byte[] encoded = codec.encode(rec);
                    context.produceRecord(OUTPUT_1, rec.getCommandId(), encoded);
                });
                context.askForCheckpoint();
            }
        }

        context.askForCheckpoint();
    }

    @Override
    public void batchFailure(ComputationContext context, String inputStream, List<Record> records) {
        String commandId = records.isEmpty() ? "" : records.get(0).getKey();
        log.warn("Batch failure \"{}\" batch of {} records with command {}.", metadata.name(), records.size(),
                commandId);
        Codec<ExportRecord> codec = getAvroCodec(ExportRecord.class);
        log.warn("Mark as failed {} Export Records for command ID {}", records.size(), commandId);
        records.forEach(rec -> {
            ExportRecord decoded = codec.decode(rec.getData());
            decoded.setFailed(true);
            byte[] encoded = codec.encode(decoded);
            context.produceRecord(OUTPUT_1, decoded.getCommandId(), encoded);
        });
        context.askForCheckpoint();
    }
}
