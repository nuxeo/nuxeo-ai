/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.configuration.ThresholdConfiguratorDescriptor;
import org.nuxeo.common.xmap.XMap;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Descriptor;
import org.nuxeo.runtime.model.impl.ExtensionDescriptorReader;

public class PersistedConfigurationServiceImpl extends DefaultComponent implements PersistedConfigurationService {

    private static final Logger log = LogManager.getLogger(PersistedConfigurationServiceImpl.class);

    private static final ExtensionDescriptorReader reader = new ExtensionDescriptorReader();

    public static final String KEY_VALUE_STORE = "aiConfigurationKVStore";

    @Override
    public void register(Class<? extends Descriptor> clazz) {
        reader.getXMap().register(clazz);
    }

    @Override
    public void persist(String key, Descriptor contribution) throws IOException {
        XMap xmap = reader.getXMap();
        String xml = xmap.toXML(contribution);
        getStore().put(key, xml);
    }

    @Override
    public Descriptor retrieve(String key) throws IOException {
        byte[] bytes = getStore().get(key);
        XMap xmap = reader.getXMap();
        xmap.register(ThresholdConfiguratorDescriptor.class);
        Descriptor descriptor;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            descriptor = (Descriptor) xmap.load(bais);
        }

        return descriptor;
    }

    protected KeyValueStore getStore() {
        KeyValueService service = Framework.getService(KeyValueService.class);
        return service.getKeyValueStore(KEY_VALUE_STORE);
    }
}
