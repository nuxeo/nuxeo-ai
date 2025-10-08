package org.nuxeo.ai.services;

import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AIConstants;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.search.SearchQuery;
import org.nuxeo.ecm.core.search.SearchResponse;
import org.nuxeo.ecm.core.search.SearchService;
import org.nuxeo.runtime.api.Framework;

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

    public static final String ES_BASE_URL_PROPERTY = "elasticsearch.httpReadOnly.baseUrl";

    protected String esBaseUrl;

    @Override
    public String usage(CoreSession session, AIConstants.AUTO type, String modelId) {
        try {
            // Build NXQL query to search audit events
            String nxqlQuery = String.format(
                "SELECT * FROM LogEntry WHERE eventId = '%s' AND extended.model = '%s'",
                type.eventName(), modelId);

            // Use the new SearchService with the enhanced index for better performance
            SearchService searchService = Framework.getService(SearchService.class);
            SearchQuery query = SearchQuery.builder(nxqlQuery, session)
                .index("enhanced") // Use enhanced index if available, fallback to repository
                .build();

            SearchResponse response = searchService.search(query);

            // For now, return a simple JSON response with the count
            // In a full implementation, you might want to aggregate by date as in the original template
            return String.format("{\"total\": %d, \"hits\": %d}",
                response.getTotal(), response.getHitsCount());

        } catch (Exception e) {
            log.error("Error when trying to search audit index for model usage data", e);
            return null;
        }
    }
}
