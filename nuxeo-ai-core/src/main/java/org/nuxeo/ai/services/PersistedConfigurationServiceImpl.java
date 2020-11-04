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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.configuration.ThresholdConfiguratorDescriptor;
import org.nuxeo.common.xmap.DOMSerializer;
import org.nuxeo.common.xmap.XMap;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Descriptor;
import org.nuxeo.runtime.model.impl.ExtensionDescriptorReader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class PersistedConfigurationServiceImpl extends DefaultComponent implements PersistedConfigurationService {

    private static final Logger log = LogManager.getLogger(PersistedConfigurationServiceImpl.class);

    private static final ExtensionDescriptorReader reader = new ExtensionDescriptorReader();

    protected final DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();

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
        if (bytes == null) {
            return null;
        }
        XMap xmap = reader.getXMap();
        Descriptor descriptor;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            descriptor = (Descriptor) xmap.load(bais);
        }
        return descriptor;
    }

    @Override
    public String retrieveConfVar(String key) {
        return new String(getStore().get(key));
    }

    @Override
    public void persistConfVar(String key, String value) {
        getStore().put(key, value);
    }

    @Override
    public List<Descriptor> retrieveAllDescriptors() throws IOException {
        Set<String> keys = getAllKeys();
        List<Descriptor> descriptors = new ArrayList<>(keys.size());
        XMap xmap = reader.getXMap();

        for (byte[] bytes : getStore().get(keys).values()) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                descriptors.add((Descriptor) xmap.load(bais));
            }
        }

        return descriptors;
    }

    @Override
    public String toXML(String tag, List<? extends Descriptor> descriptors) throws IOException {
        String empty = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><" + tag + "/>";
        if (descriptors.isEmpty())
            return empty;
        // create root element
        XMap xmap = reader.getXMap();
        DocumentBuilder docBuilder;
        Element root;
        try {
            docBuilder = dbfac.newDocumentBuilder();
            Document doc = docBuilder.newDocument();
            root = doc.createElement(tag);
            for (Descriptor descriptor : descriptors) {
                xmap.toXML(descriptor, root);
            }
        } catch (ParserConfigurationException e) {
            log.error("Cannot serialize in XML", e);
            return empty;
        }
        return DOMSerializer.toStringOmitXml(root);
    }

    @Override
    public void remove(String id) {
        getStore().put(id, (byte[]) null, 1L);
        this.removeFromKeys(id);
    }

    @Override
    public void removeFromKeys(String contribKey) {
        byte[] bytes = getStore().get(ALL_KEYS);
        if (bytes != null) {
            Predicate<String> isEntry = item -> item.equals(contribKey);
            String allKeys = new String(bytes, StandardCharsets.UTF_8);
            List<String> allKeysList = new ArrayList<>(Arrays.asList(allKeys.split(",")));
            allKeysList.removeIf(isEntry);
            allKeys = String.join("'", allKeysList);
            getStore().put(ALL_KEYS, allKeys);
        }
    }

    public void clear() {
        getAllKeys().forEach(key -> getStore().put(key, (byte[]) null));
    }

    protected Set<String> getAllKeys() {
        byte[] bytes = getStore().get(ALL_KEYS);
        if (bytes == null) {
            return new HashSet<>();
        }
        String allKeys = new String(bytes, StandardCharsets.UTF_8);
        return new HashSet<>(Arrays.asList(allKeys.split(",")));
    }

    protected KeyValueStore getStore() {
        KeyValueService service = Framework.getService(KeyValueService.class);
        return service.getKeyValueStore(KEY_VALUE_STORE);
    }
}
