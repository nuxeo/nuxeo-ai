package org.nuxeo.ai.services;

import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AIConstants;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.elasticsearch.http.readonly.HttpClient;
import org.nuxeo.elasticsearch.http.readonly.filter.DefaultSearchRequestFilter;
import org.nuxeo.elasticsearch.http.readonly.filter.SearchRequestFilter;
import org.nuxeo.elasticsearch.http.readonly.service.RequestFilterService;
import org.nuxeo.runtime.api.Framework;

public class ModelUsageServiceImpl implements ModelUsageService {

    private static final Logger log = LogManager.getLogger(ModelUsageServiceImpl.class);

    protected static final String DEFAULT_ES_BASE_URL = "http://localhost:9200/";

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

    public static final String ES_BASE_URL_PROPERTY = "elasticsearch.httpReadOnly.baseUrl";

    protected String esBaseUrl;

    @Override
    public String usage(CoreSession session, AIConstants.AUTO type, String modelId) {
        RequestFilterService requestFilterService = Framework.getService(RequestFilterService.class);
        try {
            SearchRequestFilter req = requestFilterService.getRequestFilters(INDICES);
            if (req == null) {
                req = new DefaultSearchRequestFilter();
            }

            String payload = String.format(AGGREGATE_BY_DATE_TEMPL, type.eventName(), modelId);
            req.init(session, INDICES, RAW_QUERY, payload);
            log.debug(req);
            return HttpClient.get(getElasticsearchBaseUrl() + req.getUrl(), req.getPayload());
        } catch (ReflectiveOperationException | IOException e) {
            log.error("Error when trying to get Search Request Filter for indices {}", INDICES, e);
            return null;
        }
    }

    protected String getElasticsearchBaseUrl() {
        if (esBaseUrl == null) {
            esBaseUrl = Framework.getProperty(ES_BASE_URL_PROPERTY, DEFAULT_ES_BASE_URL);
        }
        return esBaseUrl;
    }
}
