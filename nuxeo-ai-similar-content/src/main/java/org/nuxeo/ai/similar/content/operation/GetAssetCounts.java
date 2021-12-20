/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo
 */

package org.nuxeo.ai.similar.content.operation;

import static org.nuxeo.ai.similar.content.DedupConstants.CONF_DEDUPLICATION_CONFIGURATION;
import static org.nuxeo.ai.similar.content.DedupConstants.DEDUPLICATION_FACET;
import static org.nuxeo.ai.similar.content.DedupConstants.DEFAULT_CONFIGURATION;

import javax.ws.rs.core.Response;
import org.elasticsearch.search.SearchHit;
import org.nuxeo.ai.similar.content.services.SimilarContentService;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.elasticsearch.api.ElasticSearchService;
import org.nuxeo.elasticsearch.api.EsScrollResult;
import org.nuxeo.elasticsearch.query.NxQueryBuilder;
import org.nuxeo.runtime.api.Framework;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Gets asset counts (indexed/not indexed)
 */
@Operation(id = GetAssetCounts.ID, category = "AI", label = "Gets Asset Counts", description = "Gets asset counts (indexed/not indexed)")
public class GetAssetCounts {

    public static final String ID = "AI.GetAssetCounts";

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    @Context
    protected CoreSession session;

    @Context
    protected SimilarContentService scs;

    @Context
    protected ElasticSearchService es;

    @OperationMethod
    public Response run() throws JsonProcessingException {
        String configuration = Framework.getProperty(CONF_DEDUPLICATION_CONFIGURATION, DEFAULT_CONFIGURATION);
        String query = scs.getQuery(configuration);
        long totalAssetsCount = getTotalHits(query);
        // Filter by Deduplicable facet
        StringBuilder facetFilterBuilder = new StringBuilder();
        facetFilterBuilder.append(query);
        if (!query.contains("WHERE")) {
            facetFilterBuilder.append(" WHERE ");
        } else {
            facetFilterBuilder.append(" AND ");
        }
        facetFilterBuilder.append("ecm:mixinType NOT IN ('" + DEDUPLICATION_FACET + "')");
        long indexedAssetsCount = getTotalHits(facetFilterBuilder.toString());
        long nonIndexedAssetsCount = totalAssetsCount - indexedAssetsCount;
        return Response.ok(
                               MAPPER.writeValueAsString(new Counts(totalAssetsCount, indexedAssetsCount, nonIndexedAssetsCount)))
                       .build();
    }

    protected long getTotalHits(String query) {
        EsScrollResult esScroll = es.scroll(
                (new NxQueryBuilder(this.session)).nxql(query).limit(1).onlyElasticsearchResponse(), 10);
        return esScroll.getElasticsearchResponse().getHits().totalHits;
    }

    protected static class Counts {

        long totalAssetsCount;

        long indexedAssetsCount;

        long nonIndexedAssetsCount;

        public Counts(long totalAssetsCount, long indexedAssetsCount, long nonIndexedAssetsCount) {
            this.totalAssetsCount = totalAssetsCount;
            this.indexedAssetsCount = indexedAssetsCount;
            this.nonIndexedAssetsCount = nonIndexedAssetsCount;
        }

        public long getTotalAssetsCount() {
            return totalAssetsCount;
        }

        public long getIndexedAssetsCount() {
            return indexedAssetsCount;
        }

        public long getNonIndexedAssetsCount() {
            return nonIndexedAssetsCount;
        }
    }

}
