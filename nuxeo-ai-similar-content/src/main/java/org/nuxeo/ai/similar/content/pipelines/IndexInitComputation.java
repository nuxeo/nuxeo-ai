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
import static org.nuxeo.ai.pipes.functions.PropertyUtils.FILE_CONTENT;
import static org.nuxeo.ai.similar.content.pipelines.IndexAction.XPATH_PARAM;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.similar.content.pipelines.objects.IndexRecord;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.lib.stream.codec.Codec;
import org.nuxeo.lib.stream.computation.ComputationContext;

/**
 * BAF Computation to index given documents
 */
public class IndexInitComputation extends AbstractBulkComputation {

    private static final Logger log = LogManager.getLogger(IndexInitComputation.class);

    public static final String INIT_INDEX_COMPUTATION_NAME = "ai/dedup_init_index";

    protected List<IndexRecord> records = new LinkedList<>();

    public IndexInitComputation() {
        super(INIT_INDEX_COMPUTATION_NAME, 1);
    }

    @Override
    protected void compute(CoreSession session, List<String> ids, Map<String, Serializable> params) {
        log.info("Received batch to index of size {}", ids.size());
        String xpath = (String) params.getOrDefault(XPATH_PARAM, FILE_CONTENT);
        ids.stream().filter(id -> session.exists(new IdRef(id))).forEach(id -> {
            records.add(IndexRecord.of(id, command.getId(), xpath));
        });
    }

    @Override
    public void endBucket(ComputationContext ctx, BulkStatus delta) {
        Codec<IndexRecord> codec = getAvroCodec(IndexRecord.class);
        records.forEach(record -> ctx.produceRecord(OUTPUT_1, command.getId(), codec.encode(record)));
        records.clear();
        ctx.askForCheckpoint();
    }
}
