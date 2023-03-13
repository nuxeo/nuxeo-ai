package org.nuxeo.ai.services;

import java.io.IOException;

import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AIConstants;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.elasticsearch.api.ESClient;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.elasticsearch.client.ESRestClient;
import org.nuxeo.elasticsearch.http.readonly.filter.DefaultSearchRequestFilter;
import org.nuxeo.elasticsearch.http.readonly.filter.SearchRequestFilter;
import org.nuxeo.elasticsearch.http.readonly.service.RequestFilterService;
import org.nuxeo.runtime.api.Framework;
import org.opensearch.client.Request;
import org.opensearch.client.Response;

public class ModelUsageServiceImpl implements ModelUsageService {

    private static final Logger log = LogManager.getLogger(ModelUsageServiceImpl.class);

    protected static final String INDICES = "audit";

    protected static final String RAW_QUERY = "pretty";

    protected static final String AGGREGATE_BY_DATE_TEMPL = "{\n" //
            + "    \"aggs\": {\n" //
            + "        \"by\": {\n"//
            + "            \"date_histogram\": {\n"//
            + "                \"field\": \"eventDate\",\n"//
            + "                \"format\": \"yyyy-MM-dd'T'HH:mm:ss.SSSZ\",\n"//
            + "                \"interval\": \"day\",\n" //
            + "                \"min_doc_count\": 0\n"//
            + "            }\n" //
            + "        }\n" //
            + "    },\n" //
            + "    \"query\": {\n" //
            + "        \"bool\": {\n"//
            + "            \"must\": [\n" //
            + "                {\n" //
            + "                    \"term\": {\n"//
            + "                        \"eventId\": \"%s\"\n" //
            + "                    }\n"//
            + "                },\n" //
            + "                {\n" //
            + "                    \"term\": {\n"//
            + "                        \"extended.model\": \"%s\"\n" //
            + "                    }\n"//
            + "                }\n" //
            + "            ]\n" //
            + "        }\n" //
            + "    }\n" //
            + "}";//

    @Override
    public String usage(CoreSession session, AIConstants.AUTO type, String modelId) {
        RequestFilterService requestFilterService = Framework.getService(RequestFilterService.class);
        try {
            SearchRequestFilter filter = requestFilterService.getRequestFilters(INDICES);
            if (filter == null) {
                filter = new DefaultSearchRequestFilter();
            }

            String payload = String.format(AGGREGATE_BY_DATE_TEMPL, type.eventName(), modelId);
            filter.init(session, INDICES, RAW_QUERY, payload);
            log.debug(filter);

            ESClient esClient = Framework.getService(ElasticSearchAdmin.class).getClient();
            if (!(esClient instanceof ESRestClient)) {
                throw new IllegalStateException("Passthrough works only with a RestClient");
            }
            ESRestClient client = (ESRestClient) esClient;
            Request request = new Request("GET", filter.getUrl());
            if (payload != null) {
                request.setJsonEntity(payload);
            }
            Response response = client.performRequestWithTracing(request);
            return EntityUtils.toString(response.getEntity());
        } catch (ReflectiveOperationException e) {
            log.error("Error when trying to get Search Request Filter for indices {}", INDICES, e);
            return null;
        }  catch (IOException e) {
            throw new NuxeoException("Cannot parse response: " + e);
        }
    }

}
