/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.listeners;

import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.model.export.DatasetExportService;
import org.nuxeo.ecm.core.api.CloseableCoreSession;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.runtime.api.Framework;

/**
 * Asynchronous listener acting upon `startContinuousExport` event Performs REST Calls via {@link CloudClient} to obtain
 * all AI models and their corpora. The processing includes NXQL query modification for excluding previously exported
 * documents {@link DatasetExportService} will be called if and only if minimum documents were found in the repository
 */
public class ContinuousExportListener implements PostCommitEventListener {

    private static final Logger log = LogManager.getLogger(ContinuousExportListener.class);

    public static final String ENTRIES_KEY = "entries";

    public static final String START_CONTINUOUS_EXPORT = "startContinuousExport";

    public static final String FORCE_EXPORT = "forceContinuousExport";

    public static final String NUXEO_AI_CONTINUOUS_EXPORT_ENABLE = "nuxeo.ai.continuous.export.enable";

    @Override
    public void handleEvent(EventBundle eb) {
        if (eb == null || eb.isEmpty()) {
            return;
        }

        if (!Boolean.parseBoolean(Framework.getProperty(NUXEO_AI_CONTINUOUS_EXPORT_ENABLE))) {
            EventContext eventContext = eb.peek().getContext();
            if (!eventContext.hasProperty(FORCE_EXPORT)) {
                return;
            }
        }

        CloudClient client = Framework.getService(CloudClient.class);
        String repository = Framework.getService(RepositoryManager.class).getDefaultRepository().getName();
        try (CloseableCoreSession session = CoreInstance.openCoreSessionSystem(repository)) {
            Set<String> uids = getModelIds(session, client);
            if (uids.isEmpty()) {
                log.warn("Models listing is empty for continuous export");
                return;
            }

            DatasetExportService exportService = Framework.getService(DatasetExportService.class);
            exportService.export(session, uids);
        }
    }

    /**
     * @param client {@link CloudClient} Nuxeo Insight Cloud Client for REST communication
     * @return A {@link List} of AI_Model ids
     */
    protected Set<String> getModelIds(CoreSession session, CloudClient client) {
        try {
            JSONBlob models = client.getModelsByDatasource(session);
            @SuppressWarnings("unchecked")
            Map<String, Serializable> resp = MAPPER.readValue(models.getStream(), Map.class);
            if (resp.containsKey(ENTRIES_KEY)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Serializable>> entries = (List<Map<String, Serializable>>) resp.get(ENTRIES_KEY);
                return entries.stream().map(entry -> (String) entry.get("uid")).collect(Collectors.toSet());
            } else {
                log.debug("Could not find any entries in " + models.getString());
            }
        } catch (IOException e) {
            log.error(e);
        }

        return Collections.emptySet();
    }
}
