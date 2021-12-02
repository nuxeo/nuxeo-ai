/*
 * (C) Copyright 2006-2021 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 * Contributors:
 *    Andrei Nechaev
 *
 */

package org.nuxeo.ai.similar.content.pipelines;

import static org.nuxeo.ai.bulk.ExportHelper.getAvroCodec;
import static org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation.updateStatus;

import java.io.IOException;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.sdk.rest.exception.InvalidParametersException;
import org.nuxeo.ai.similar.content.pipelines.objects.IndexRecord;
import org.nuxeo.ai.similar.content.services.SimilarContentService;
import org.nuxeo.ecm.core.api.CloseableCoreSession;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.lib.stream.codec.Codec;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Computation responsible for sending given document to index
 */
public class IndexComputation extends AbstractComputation {

    private static final Logger log = LogManager.getLogger(IndexComputation.class);

    public static final String INDEX_COMPUTATION_NAME = "dedup_index";

    public IndexComputation() {
        super(INDEX_COMPUTATION_NAME, 1, 1);
    }

    protected static void reportStatus(ComputationContext ctx, BulkStatus status, BulkStatus delta, boolean success) {
        delta.setProcessingEndTime(Instant.now());
        delta.setProcessed(1);
        delta.setErrorCount(success ? 0 : 1);
        updateStatus(ctx, delta);
    }

    @Override
    public void processRecord(ComputationContext ctx, String in, Record record) {
        Codec<IndexRecord> codec = getAvroCodec(IndexRecord.class);
        IndexRecord ir = codec.decode(record.getData());

        BulkService service = Framework.getService(BulkService.class);
        BulkCommand command = service.getCommand(ir.getCommandId());

        TransactionHelper.runInTransaction(() -> {
            try (CloseableCoreSession session = CoreInstance.openCoreSession(command.getRepository(),
                    command.getUsername())) {
                IdRef ref = new IdRef(ir.getDocId());
                if (!session.exists(ref)) {
                    log.error("Cannot index document {}; provided document was removed", ir.getDocId());
                }

                DocumentModel document = session.getDocument(ref);
                SimilarContentService scs = Framework.getService(SimilarContentService.class);
                BulkStatus status = service.getStatus(ir.getCommandId());
                BulkStatus delta = BulkStatus.deltaOf(ir.getCommandId());
                if (status.getProcessingStartTime() == null) {
                    delta.setProcessingStartTime(Instant.now());
                }

                boolean success = scs.index(document, ir.getXpath());
                reportStatus(ctx, status, delta, success);
            } catch (IOException | InvalidParametersException e) {
                log.error("An error occurred during Insight API for document {}", ir.getDocId(), e);
                throw new NuxeoException(e);
            }
        });

        ctx.askForCheckpoint();
    }
}
