/*
 * (C) Copyright 2006-2021 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 * Contributors:
 *    Andrei Nechaev
 *
 */
package org.nuxeo.ai.similar.content.services;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.similar.content.configuration.DeduplicationDescriptor;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

public class SimilarServiceComponent extends DefaultComponent implements SimilarContentService {

    private static final Logger log = LogManager.getLogger(SimilarServiceComponent.class);

    public static final String DEDUPLICATION_CONFIG_XP = "configuration";

    protected final Map<String, DeduplicationDescriptor> dedupDescriptors = new HashMap<>();

    @Override
    public void registerContribution(Object contribution, String xp, ComponentInstance component) {
        if (DEDUPLICATION_CONFIG_XP.equals(xp)) {
            DeduplicationDescriptor desc = (DeduplicationDescriptor) contribution;
            dedupDescriptors.put(desc.getName(), desc);
        }
    }

    @Override
    public boolean test(String config, DocumentModel doc) {
        if (!dedupDescriptors.containsKey(config)) {
            log.warn("No such configuration: {}", config);
            return false;
        }

        return Stream.of(dedupDescriptors.get(config).getFilters()).allMatch(filter -> filter.accept(doc));
    }

    @Override
    public boolean anyMatch(DocumentModel doc) {
        return dedupDescriptors.values()
                               .stream()
                               .anyMatch(d -> Arrays.stream(d.getFilters()).allMatch(filter -> filter.accept(doc)));
    }

    @Override
    public String getQuery(String name) {
        return dedupDescriptors.get(name).getQuery();
    }

    @Override
    public String getXPath(String name) {
        return dedupDescriptors.get(name).getXPath();
    }
}
