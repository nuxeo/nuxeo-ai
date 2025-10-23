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
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.bulk.BulkCodecs;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.audit.service.AuditBackend;

public class ExportDoneComputation extends AbstractComputation {

    protected static final String EXPORT_DONE_EVENT = "exportDone";

    private static final Logger log = LogManager.getLogger(ExportDoneComputation.class);

    public ExportDoneComputation(String name) {
        super(name, 1, 0);
    }

    @Override
    public void processRecord(ComputationContext ctx, String input, Record record) {
        BulkStatus status = BulkCodecs.getStatusCodec().decode(record.getData());
        if (EXPORT_ACTION_NAME.equals(status.getAction()) && COMPLETED.equals(status.getState())) {
            BulkCommand cmd = Framework.getService(BulkService.class).getCommand(status.getId());
            if (cmd == null) {
                log.error("No Bulk Command found for {}", status.getId());
                return;
            }

            Message message = log.getMessageFactory()
                                 .newMessage(EXPORT_ACTION_NAME
                                                 + " for commandId {} has completed.\nProcessed {} records with {} errors",
                                         cmd.getId(), status.getProcessed(), status.getErrorCount());
            log.warn(message.getFormattedMessage());

            AuditBackend logger = Framework.getService(AuditBackend.class);
            if (logger != null) {
                LogEntry entry = LogEntry.builder(EXPORT_DONE_EVENT, new Date())
                                         .category(EXPORT_ACTION_NAME)
                                         .comment(message.getFormattedMessage())
                                         .build();
                logger.addLogEntries(Collections.singletonList(entry));
            }

            CloudClient cc = Framework.getService(CloudClient.class);
            TransactionHelper.runInTransaction(() -> {
                CoreSession session = CoreInstance.getCoreSessionSystem(cmd.getRepository(), cmd.getUsername());
                if (!cc.notifyOnExportDone(session, cmd.getId())) {
                    log.error("Could not notify Cloud on export done event; commandId " + cmd.getId());
                }
            });
        }

        ctx.askForCheckpoint();
    }
}
