/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Gethin James
 */
package org.nuxeo.ai.textract;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import org.nuxeo.ai.pipes.streams.Initializable;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.model.Descriptor;

@XObject("textract")
public class TextractProcessorDescriptor implements Descriptor {

    @XNode("@id")
    protected String id;

    @XNode("@serviceName")
    protected String serviceName;

    @XNodeMap(value = "option", key = "@name", type = HashMap.class, componentType = String.class)
    protected Map<String, String> options = new HashMap<>();

    @XNode("@class")
    protected Class<? extends TextractProcessor> processor;

    @Override
    public String getId() {
        return id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public TextractProcessor getInstance() {
        try {
            TextractProcessor processorInstance = processor.getDeclaredConstructor().newInstance();
            if (processorInstance instanceof Initializable) {
                ((Initializable) processorInstance).init(options);
            }
            return processorInstance;
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException | InstantiationException e) {
            throw new NuxeoException(String.format("TextractProcessorDescriptor for %s must define a valid TextractProcessor", id), e);
        }
    }
}
