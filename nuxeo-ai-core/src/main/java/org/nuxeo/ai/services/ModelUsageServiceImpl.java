package org.nuxeo.ai.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AIConstants;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.api.Framework;

public class ModelUsageServiceImpl implements ModelUsageService {

    private static final Logger log = LogManager.getLogger(ModelUsageServiceImpl.class);

    public static final String ES_BASE_URL_PROPERTY = "elasticsearch.httpReadOnly.baseUrl";

    protected String esBaseUrl;

    @Override
    public String usage(CoreSession session, AIConstants.AUTO type, String modelId) {
        try {
            String nxqlQuery = String.format(
                    "SELECT * FROM LogEntry WHERE eventId = '%s' AND extended.model = '%s'",
                    type.eventName(), modelId);

            // Use adapter abstraction (centralized search logic)
            SearchAdapterService adapter = Framework.getService(SearchAdapterService.class);
            SearchSummary summary = adapter.search(session, nxqlQuery, SearchOptions.builder()
                                                                                   .index(SearchAdapterService.DEFAULT_INDEX)
                                                                                   .limit(0) // we only need counts
                                                                                   .build());
            return String.format("{\"total\": %d, \"hits\": %d}", summary.getTotal(), summary.getHitsCount());
        } catch (Exception e) {
            log.error("Error when trying to search audit index for model usage data", e);
            return null;
        }
    }
}
