/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.bulk;

import static org.nuxeo.ai.AIConstants.EXPORT_ACTION_NAME;
import static org.nuxeo.ecm.core.bulk.message.BulkStatus.State.COMPLETED;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.Message;
import org.nuxeo.ecm.core.bulk.BulkCodecs;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;

public class ExportDoneComputation extends AbstractComputation {

    private static final Logger log = LogManager.getLogger(ExportDoneComputation.class);

    protected static final String EXPORT_DONE_EVENT = "exportDone";

    public ExportDoneComputation(String name) {
        super(name, 1, 0);
    }

    @Override
    public void processRecord(ComputationContext ctx, String input, Record record) {
        BulkStatus status = BulkCodecs.getStatusCodec().decode(record.getData());
        if (EXPORT_ACTION_NAME.equals(status.getAction()) && COMPLETED.equals(status.getState())) {
            BulkCommand cmd = Framework.getService(BulkService.class).getCommand(status.getId());
            Message message = log.getMessageFactory().newMessage(EXPORT_ACTION_NAME
                            + " for commandId {} has completed.\nProcessed {} records with {} errors",
                    cmd.getId(), status.getProcessed(), status.getErrorCount());

            log.warn(message.getFormattedMessage());

            AuditLogger logger = Framework.getService(AuditLogger.class);
            LogEntry entry = logger.newLogEntry();
            entry.setCategory(EXPORT_ACTION_NAME);
            entry.setComment(message.getFormattedMessage());
            entry.setEventId(EXPORT_DONE_EVENT);

            Instant endTime = status.getProcessingEndTime();
            if (endTime != null) {
                long endMs = endTime.toEpochMilli();
                entry.setEventDate(new Date(endMs));
            }

            logger.addLogEntries(Collections.singletonList(entry));
        }

        ctx.askForCheckpoint();
    }
}
