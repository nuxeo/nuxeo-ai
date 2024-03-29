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

    public static final String TOPIC_CONF = "ai-configuration-conf";

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        PubSubService pubSubService = Framework.getService(PubSubService.class);
        if (pubSubService != null) {
            pubSubService.registerSubscriber(AIConfigurationServiceImpl.TOPIC_CONF, this::confVarPublisher);
        } else {
            log.warn("No Pub/Sub service available");
        }
    }

    protected void confVarPublisher(String topic, byte[] bytes) {
        String contribKey = new String(bytes);
        PersistedConfigurationService pcs = Framework.getService(PersistedConfigurationService.class);
        Framework.getProperties().put(contribKey, pcs.retrieveConfVar(contribKey));
    }

    @Override
    public String set(Descriptor desc) throws IOException {
        PersistedConfigurationService pcs = Framework.getService(PersistedConfigurationService.class);
        pcs.persist(desc.getId(), desc);
        publish(desc.getId().getBytes());
        return desc.getId();
    }

    @Override
    public String set(String key, String xml) {
        PersistedConfigurationService pcs = Framework.getService(PersistedConfigurationService.class);
        pcs.persist(key, xml);
        publish(key.getBytes());
        return key;
    }

    @Override
    public String setConfVar(String key, String value) {
        PersistedConfigurationService pcs = Framework.getService(PersistedConfigurationService.class);
        pcs.persistConfVar(key, value);
        publishConf(key.getBytes());
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

    protected void publishConf(byte[] bytes) {
        PubSubService service = Framework.getService(PubSubService.class);
        if (service != null) {
            service.publish(TOPIC_CONF, bytes);
        } else {
            log.warn("No Pub/Sub service available");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Descriptor> T get(String key, Class<T> clazz) throws IOException {
        PersistedConfigurationService pcs = Framework.getService(PersistedConfigurationService.class);
        Descriptor desc = pcs.retrieve(key);
        if (desc != null && desc.getClass().isAssignableFrom(clazz)) {
            return (T) desc;
        }

        return null;
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
    public <T extends Descriptor> String getXML(String tag, Class<T> clazz) throws IOException {
        List<T> all = getAll(clazz);
        return Framework.getService(PersistedConfigurationService.class).toXML(tag, all);
    }

    @Override
    public void remove(String id) {
        Framework.getService(PersistedConfigurationService.class).remove(id);
        publish(id.getBytes());
    }
}
