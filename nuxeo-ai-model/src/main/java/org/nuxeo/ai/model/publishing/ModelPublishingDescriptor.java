/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     Pedro Cardoso
 *     Gethin James
 */
package org.nuxeo.ai.model.publishing;

import java.util.HashMap;
import java.util.Map;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * Definition of an AI model Publisher Extension
 */
@XObject("publisher")
public class ModelPublishingDescriptor {

    @XNode("@id")
    public String id;

    @XNode("@default")
    public boolean isDefault;

    /**
     * Configuration options
     */
    @XNodeMap(value = "option", key = "@name", type = HashMap.class, componentType = String.class)
    public Map<String, String> options = new HashMap<>();

    @XNode("@class")
    protected Class<? extends ModelPublisherExtension> clazz;

    public ModelPublisherExtension getInstance() {
        try {
            ModelPublisherExtension publisher = clazz.getConstructor().newInstance();
            publisher.init(options);
            return publisher;
        } catch (ReflectiveOperationException | NullPointerException e) {
            throw new NuxeoException(String.format("ModelPublisherExtension for %s is invalid.", id), e);
        }
    }
}
