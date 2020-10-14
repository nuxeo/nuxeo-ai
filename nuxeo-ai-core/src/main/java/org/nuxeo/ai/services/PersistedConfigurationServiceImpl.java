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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
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

    public static final String ALL_KEYS = "KEYS";

    public PersistedConfigurationServiceImpl() {
        this.register(ThresholdConfiguratorDescriptor.class);
    }

    @Override
    public void register(Class<? extends Descriptor> clazz) {
        reader.getXMap().register(clazz);
    }

    @Override
    public void persist(String key, Descriptor contribution) throws IOException {
        XMap xmap = reader.getXMap();
        String xml = xmap.toXML(contribution);
        this.persist(key, xml);
    }

    @Override
    public void persist(String key, String xml) {
        // Store descriptor
        getStore().put(key, xml);
        // Store key reference
        Set<String> keys = getAllKeys();
        keys.add(key);
        String allKeys = String.join(",", keys);
        getStore().put(ALL_KEYS, allKeys);
    }

    @Override
    public Descriptor retrieve(String key) throws IOException {
        byte[] bytes = getStore().get(key);
        XMap xmap = reader.getXMap();
        Descriptor descriptor;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            descriptor = (Descriptor) xmap.load(bais);
        }
        return descriptor;
    }

    @Override
    public Pair<String, List<Descriptor>> retrieveAllDescriptors() throws IOException {
        Set<String> keys = getAllKeys();
        List<Descriptor> descriptors = new ArrayList<>();
        String xmlPayLoad = "";
        for (String key : keys) {
            byte[] bytes = getStore().get(key);
            XMap xmap = reader.getXMap();
            xmlPayLoad.concat(new String(bytes));
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                descriptors.add((Descriptor) xmap.load(bais));
            }
        }
        return new ImmutablePair<>(xmlPayLoad, descriptors);
    }

    protected Set<String> getAllKeys() {
        String allKeys = new String(getStore().get(ALL_KEYS));
        return new HashSet<>(Arrays.asList(allKeys.split(",")));
    }

    protected KeyValueStore getStore() {
        KeyValueService service = Framework.getService(KeyValueService.class);
        return service.getKeyValueStore(KEY_VALUE_STORE);
    }
}
