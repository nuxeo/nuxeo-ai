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

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.configuration.ThresholdConfiguratorDescriptor;
import org.nuxeo.ai.configuration.ThresholdService;
import org.nuxeo.ecm.core.api.NuxeoException;
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

    protected String TOPIC = "ai-configuration";

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        PubSubService pubSubService = Framework.getService(PubSubService.class);
        if (pubSubService != null) {
            pubSubService.registerSubscriber(TOPIC, this::thresholdSubscriber);
        } else {
            log.warn("No Pub/Sub service available");
        }
    }

    @Override
    public void setThresholds(ThresholdConfiguratorDescriptor thresholds) throws IOException {
        String key = UUID.randomUUID().toString();
        PersistedConfigurationService pcs = Framework.getService(PersistedConfigurationService.class);
        pcs.persist(key, thresholds);
        publish(key.getBytes());
    }

    @Override
    public void setThresholds(String thresholdsXML) {
        String key = UUID.randomUUID().toString();
        PersistedConfigurationService pcs = Framework.getService(PersistedConfigurationService.class);
        pcs.persist(key, thresholdsXML);
        publish(key.getBytes());
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
    public List<ThresholdConfiguratorDescriptor> getAllThresholds() throws IOException {
        PersistedConfigurationService pcs = Framework.getService(PersistedConfigurationService.class);
        Pair<String, List<Descriptor>> allDescriptors = pcs.retrieveAllDescriptors();
        return allDescriptors.getRight()
                             .stream()
                             .filter(descriptor -> descriptor instanceof ThresholdConfiguratorDescriptor)
                             .map(descriptor -> (ThresholdConfiguratorDescriptor) descriptor)
                             .collect(Collectors.toList());
    }

    @Override
    public String getAllThresholdsXML() throws IOException {
        return Framework.getService(PersistedConfigurationService.class).retrieveAllDescriptors().getLeft();
    }

    protected void thresholdSubscriber(String topic, byte[] message) {
        String contribKey = new String(message);
        ThresholdService service = Framework.getService(ThresholdService.class);
        PersistedConfigurationService pcs = Framework.getService(PersistedConfigurationService.class);
        try {
            ThresholdConfiguratorDescriptor thresholdConfiguratorDescriptor = (ThresholdConfiguratorDescriptor) pcs.retrieve(
                    contribKey);
            service.reload(thresholdConfiguratorDescriptor);
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }
}