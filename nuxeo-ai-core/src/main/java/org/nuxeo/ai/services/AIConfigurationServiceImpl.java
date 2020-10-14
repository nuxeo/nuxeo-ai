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
import org.nuxeo.ai.configuration.ThresholdComponent;
import org.nuxeo.ai.configuration.ThresholdConfiguratorDescriptor;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.RuntimeService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.Descriptor;
import org.nuxeo.runtime.pubsub.PubSubService;

/**
 * AI Business service layer to store all "AI" components.
 *
 * @since 2.1.4
 */
public class AIConfigurationServiceImpl implements AIConfigurationService {

    public static final String COMPONENT_NAME = "org.nuxeo.ai.configuration.ThresholdComponent";

    protected final PubSubService pubSubService;

    protected RuntimeService runtimeService;

    protected PersistedConfigurationService persistedConfigurationService;

    protected String TOPIC = "ai-configuration";

    public AIConfigurationServiceImpl() {
        runtimeService = Framework.getRuntime();
        persistedConfigurationService = Framework.getService(PersistedConfigurationService.class);
        persistedConfigurationService.register(ThresholdConfiguratorDescriptor.class);
        pubSubService = Framework.getService(PubSubService.class);
        pubSubService.registerSubscriber(TOPIC, this::thresholdSubscriber);
    }

    @Override
    public void setThresholds(ThresholdConfiguratorDescriptor thresholds) throws IOException {
        String key = UUID.randomUUID().toString();
        persistedConfigurationService.persist(key, thresholds);
        pubSubService.publish(TOPIC, key.getBytes());
    }

    @Override
    public void setThresholds(String thresholdsXML) {
        String key = UUID.randomUUID().toString();
        persistedConfigurationService.persist(key, thresholdsXML);
        pubSubService.publish(TOPIC, key.getBytes());
    }

    @Override
    public List<ThresholdConfiguratorDescriptor> getAllThresholds() throws IOException {
        Pair<String, List<Descriptor>> allDescriptors = persistedConfigurationService.retrieveAllDescriptors();
        return allDescriptors.getRight()
                             .stream()
                             .filter(descriptor -> descriptor instanceof ThresholdConfiguratorDescriptor)
                             .map(descriptor -> (ThresholdConfiguratorDescriptor) descriptor)
                             .collect(Collectors.toList());
    }

    @Override
    public String getAllThresholdsXML() throws IOException {
        return persistedConfigurationService.retrieveAllDescriptors().getLeft();
    }

    protected void thresholdSubscriber(String topic, byte[] message) {
        String contribKey = new String(message);
        ThresholdComponent thresholdComponent = (ThresholdComponent) runtimeService.getComponent(COMPONENT_NAME);
        try {
            ThresholdConfiguratorDescriptor thresholdConfiguratorDescriptor = (ThresholdConfiguratorDescriptor) persistedConfigurationService.retrieve(
                    contribKey);
            thresholdComponent.hotReload(thresholdConfiguratorDescriptor);
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }
}