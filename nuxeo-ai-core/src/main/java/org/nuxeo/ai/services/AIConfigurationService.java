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

import org.nuxeo.runtime.model.Descriptor;

/**
 * @since 2.4.1
 */
public interface AIConfigurationService {

    /**
     * Persist a thresholds descriptor in KVS and load it in the component registry via pubsub.
     * @return
     */
    String set(Descriptor descriptor) throws IOException;

    /**
     * Persist threshold XML contribution in KVS and load it in the component registry via pubsub.
     * @return
     */
    String set(String descriptorXML);

    /**
     * @return all a pair of all persisted thresholds in xml and as objects
     */
    <T extends Descriptor> List<T> getAll(Class<T> clazz) throws IOException;

    <T extends Descriptor> String getXML(String tag, Class<T> clazz) throws IOException;
}