package org.nuxeo.ai.services;

import org.nuxeo.ai.AIConstants;
import org.nuxeo.ecm.core.api.CoreSession;

public interface ModelUsageService {

    String usage(CoreSession session, AIConstants.AUTO type, String modelId);
}
