/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.services;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Descriptor;
import org.nuxeo.runtime.pubsub.PubSubService;

/**
 * AI Business service layer to store all "AI" components.
 *
 * @since 2.1.4
 */
public class AIConfigurationServiceImpl extends DefaultComponent implements AIConfigurationService {

    private static final Logger log = LogManager.getLogger(AIConfigurationServiceImpl.class);

    public static final String TOPIC = "ai-configuration";

    @Override
    public void start(ComponentContext context) {
        super.start(context);
    }

    @Override
    public String set(Descriptor thresholds) throws IOException {
        String key = UUID.randomUUID().toString();
        PersistedConfigurationService pcs = Framework.getService(PersistedConfigurationService.class);
        pcs.persist(key, thresholds);
        publish(key.getBytes());
        return key;
    }

    @Override
    public String set(String xml) {
        String key = UUID.randomUUID().toString();
        PersistedConfigurationService pcs = Framework.getService(PersistedConfigurationService.class);
        pcs.persist(key, xml);
        publish(key.getBytes());
        return key;
    }

    protected void publish(byte[] bytes) {
        PubSubService service = Framework.getService(PubSubService.class);
        if (service != null) {
            service.publish(TOPIC, bytes);
        } else {
            log.warn("No Pub/Sub service available");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Descriptor> List<T> getAll(Class<T> clazz) throws IOException {
        PersistedConfigurationService pcs = Framework.getService(PersistedConfigurationService.class);
        List<Descriptor> allDescriptors = pcs.retrieveAllDescriptors();
        return allDescriptors.stream()
                             .filter(descriptor -> descriptor.getClass().isAssignableFrom(clazz))
                             .map(descriptor -> ((T) descriptor))
                             .collect(Collectors.toList());
    }

    @Override
    public <T extends Descriptor> String getAllXML(String tag, Class<T> clazz) throws IOException {
        List<T> all = getAll(clazz);
        return Framework.getService(PersistedConfigurationService.class).toXML(tag, all);
    }
}