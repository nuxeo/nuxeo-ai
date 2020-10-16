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

import java.io.IOException;
import java.util.List;

import org.nuxeo.runtime.model.Descriptor;

/**
 * Service for managing Nuxeo Contributions via Persistent layer
 */
public interface PersistedConfigurationService {

    /**
     * Registers object of
     *
     * @param clazz given
     */
    void register(Class<? extends Descriptor> clazz);

    /**
     * Stores a contribution under
     *
     * @param key {@link String} as unique key
     * @param contribution {@link Descriptor} as an object that conforms to the interface
     * @throws IOException if write to Persistent layer fails
     */
    void persist(String key, Descriptor contribution) throws IOException;

    /**
     * Retrieves a representation of {@link Descriptor} under
     *
     * @param key {@link String} as unique key
     * @param xml {@link String} as the xml to persist
     */
    void persist(String key, String xml);

    /**
     * Retrieves a representation of {@link Descriptor} under
     *
     * @param key {@link String} as unique key
     * @return {@link Descriptor}
     * @throws IOException if read from Persistent layer fails
     */
    Descriptor retrieve(String key) throws IOException;

    /**
     * Retrieves all loaded {@link Descriptor}s
     *
     * @return descriptors
     * @throws IOException
     */
    List<Descriptor> retrieveAllDescriptors() throws IOException;

    String toXML(String tag, List<? extends Descriptor> descriptors) throws IOException;
}
