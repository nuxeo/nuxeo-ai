package org.nuxeo.ai.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AIConstants;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModelList;

public class ModelUsageServiceImpl implements ModelUsageService {

    private static final Logger log = LogManager.getLogger(ModelUsageServiceImpl.class);

    @Override
    public String usage(CoreSession session, AIConstants.AUTO type, String modelId) {
        try {
            if (session == null || type == null || modelId == null) {
                log.warn("Invalid parameters: session, type, or modelId is null");
                return "{\"total\":0,\"hits\":0}";
            }

            String nxql = String.format(
                    "SELECT ecm:uuid FROM LogEntry WHERE eventId = '%s' AND extended.model = '%s'",
                    type.eventName(), modelId);

            DocumentModelList docs = session.query(nxql);
            long total = docs.size();

            return String.format("{\"total\": %d, \"hits\": %d}", total, total);
        } catch (Exception e) {
            log.error("Error when trying to query model usage data", e);
            return "{\"total\":0,\"hits\":0}";
        }
    }
}
