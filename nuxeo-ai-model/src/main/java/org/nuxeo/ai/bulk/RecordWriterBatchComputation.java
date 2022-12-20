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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public static final String TRAINING_WRITER = "training";

    public static final String VALIDATION_WRITER = "validation";

    protected Set<String> exportedIds = new HashSet<>();

    protected RecordWriter trainingWriter;

    protected RecordWriter validationWriter;

    public RecordWriterBatchComputation(String name) {
        super(name, 1, 1);
    }

    @Override
    public void batchProcess(ComputationContext context, String inputStream, List<Record> records) {
        Codec<ExportRecord> codec = getAvroCodec(ExportRecord.class);
        Map<String, List<ExportRecord>> grouped = records.stream()
                                                         .map(r -> codec.decode(r.getData()))
                                                         .collect(groupingBy(ExportRecord::getId, toList()));
        for (Map.Entry<String, List<ExportRecord>> entry : grouped.entrySet()) {
            List<ExportRecord> recs = entry.getValue();
            long errored;
            try {
                errored = write(context, codec, recs);
            } catch (IOException e) {
                log.error("Failed to write batch {}; exception {}", metadata.name(), e);
                throw new NuxeoException("Failed to write batch " + metadata.name(), e);
            }

            log.debug("Attempted to write {} records; Errors {}", recs.size(), errored);
        }

        exportedIds.clear();
        context.askForCheckpoint();
    }

    private long write(ComputationContext context, Codec<ExportRecord> codec, List<ExportRecord> recs)
            throws IOException {
        long errored = 0;
        for (ExportRecord rec : recs) {
            RecordWriter writer;
            if (rec.isTraining()) {
                writer = getTrainingWriter();
            } else {
                writer = getValidationWriter();
            }

            if (!writer.write(rec)) {
                rec.setFailed(true);
                errored += 1;
            }
            exportedIds.add(rec.getId());
            byte[] encoded = codec.encode(rec);
            context.produceRecord(OUTPUT_1, rec.getCommandId(), encoded);
        }

        return errored;
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
            if (!exportedIds.contains(decoded.getId())) {
                decoded.setFailed(true);
                byte[] encoded = codec.encode(decoded);
                context.produceRecord(OUTPUT_1, decoded.getId(), encoded);
            }
        });

        exportedIds.clear();
        context.askForCheckpoint();
    }

    protected RecordWriter getTrainingWriter() {
        if (trainingWriter == null) {
            trainingWriter = Framework.getService(AIComponent.class).getRecordWriter(TRAINING_WRITER);
            if (trainingWriter == null) {
                throw new NuxeoException("Unknown record write specified: " + TRAINING_WRITER);
            }
        }

        return trainingWriter;
    }

    protected RecordWriter getValidationWriter() {
        if (validationWriter == null) {
            validationWriter = Framework.getService(AIComponent.class).getRecordWriter(VALIDATION_WRITER);
            if (validationWriter == null) {
                throw new NuxeoException("Unknown record write specified: " + VALIDATION_WRITER);
            }
        }

        return validationWriter;
    }
}
