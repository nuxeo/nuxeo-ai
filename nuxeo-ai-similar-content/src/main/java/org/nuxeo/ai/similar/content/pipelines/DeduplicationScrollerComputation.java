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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.nuxeo.ai.sdk.rest.Common.Headers.SCROLL_ID_HEADER;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.sdk.objects.deduplication.ScrollableResult;
import org.nuxeo.ai.sdk.objects.deduplication.SimilarTuple;
import org.nuxeo.ai.sdk.rest.client.API;
import org.nuxeo.ai.sdk.rest.client.InsightClient;
import org.nuxeo.ai.similar.content.pojo.SimilarTupleContainer;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Computation responsible for scrolling through all duplicates available at Insight
 */
public class DeduplicationScrollerComputation extends AbstractComputation {

    private static final Logger log = LogManager.getLogger(DeduplicationScrollerComputation.class);

    protected static final ObjectMapper LOCAL_OM = new ObjectMapper();

    public static final String SCROLLER_COMPUTATION_NAME = "ai/dedup-scroller";

    public DeduplicationScrollerComputation(String name) {
        super(name, 1, 1);
    }

    @Override
    public void processRecord(ComputationContext ctx, String stream, Record record) {
        CloudClient client = Framework.getService(CloudClient.class);
        Objects.requireNonNull(client);

        String repo = record.getKey();
        String user = new String(record.getData(), UTF_8);
        CoreSession session = CoreInstance.getCoreSessionSystem(repo, user);
        InsightClient insight = client.getClient(session)
                                      .orElseThrow(() -> new NuxeoException(
                                              "Could not obtain Insight Client for user " + session.getPrincipal()
                                                                                                   .getActingUser()));
        try {
            ScrollableResult result = null;
            do {
                Map<String, Serializable> params =
                        result != null ? singletonMap(SCROLL_ID_HEADER, result.getScrollId()) : emptyMap();
                result = insight.api(API.Dedup.ALL).call(params);
                if (result != null) {
                    List<SimilarTuple> tuples = result.getResult();
                    for (SimilarTuple tuple : tuples) {
                        byte[] bytes = LOCAL_OM.writeValueAsBytes(SimilarTupleContainer.of(user, repo, tuple));
                        ctx.produceRecord(OUTPUT_1, tuple.getDocumentId(), bytes);
                    }
                }
            } while (result != null && !result.getResult().isEmpty());
        } catch (IOException e) {
            log.error("Could not execute Dedup API call", e);
            throw new NuxeoException(e);
        }

        ctx.askForCheckpoint();
    }
}
