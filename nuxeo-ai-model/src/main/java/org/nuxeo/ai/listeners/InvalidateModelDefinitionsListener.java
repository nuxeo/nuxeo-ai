/*
 *   (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *
 *   Contributors:
 *       anechaev
 */
package org.nuxeo.ai.listeners;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.model.ModelProperty;
import org.nuxeo.ai.model.serving.ModelDescriptor;
import org.nuxeo.ai.model.serving.ModelServingService;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.pubsub.PubSubService;

import static org.nuxeo.ai.model.serving.ModelServingService.INVALIDATOR_TOPIC;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.nuxeo.ai.listeners.ContinuousExportListener.ENTRIES_KEY;
import static org.nuxeo.ai.listeners.ContinuousExportListener.getRepositoryName;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

/**
 * Invalidate and Update currently running models
 */
public class InvalidateModelDefinitionsListener implements PostCommitEventListener {

    public static final String EVENT_NAME = "invalidateModelDefinitions";

    private static final Logger log = LogManager.getLogger(InvalidateModelDefinitionsListener.class);

    @Override
    public void handleEvent(EventBundle bundle) {
        log.info("Publishing AI Model invalidation event");
        PubSubService service = Framework.getService(PubSubService.class);
        if (service != null) {
            service.publish(INVALIDATOR_TOPIC, new byte[0]);
        } else {
            log.warn("No Pub/Sub service available");
        }
    }
}
