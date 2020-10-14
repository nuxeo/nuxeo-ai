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

import org.nuxeo.ai.configuration.ThresholdConfiguratorDescriptor;

/**
 * @since 2.4.1
 */
public interface AIConfigurationService {

    /**
     * Persist a thresholds descriptor in KVS and load it in the component registry via pubsub.
     */
    void setThresholds(ThresholdConfiguratorDescriptor thresholds) throws IOException;

    /**
     * Persist threshold XML contribution in KVS and load it in the component registry via pubsub.
     */
    void setThresholds(String thresholdsXML);

    /**
     * @return all a pair of all persisted thresholds in xml and as objects
     */
    List<ThresholdConfiguratorDescriptor> getAllThresholds() throws IOException;

    String getAllThresholdsXML() throws IOException;
}