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

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.similar.content.configuration.DeduplicationDescriptor;
import org.nuxeo.ai.similar.content.configuration.ResultsFilter;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

public class SimilarServiceComponent extends DefaultComponent implements SimilarContentService {

    private static final Logger log = LogManager.getLogger(SimilarServiceComponent.class);

    public static final String DEDUPLICATION_CONFIG_XP = "configuration";

    public static final String DEDUPLICATION_FILTER_CONFIG_XP = "filter";

    protected final Map<String, DeduplicationDescriptor> dedupDescriptors = new HashMap<>();

    protected final Map<String, ResultsFilter> filters = new HashMap<>();

    @Override
    public void registerContribution(Object contribution, String xp, ComponentInstance component) {
        if (DEDUPLICATION_CONFIG_XP.equals(xp)) {
            DeduplicationDescriptor desc = (DeduplicationDescriptor) contribution;
            dedupDescriptors.put(desc.getName(), desc);
        } else if (DEDUPLICATION_FILTER_CONFIG_XP.equals(xp)) {
            ResultsFilter filter = (ResultsFilter) contribution;
            filters.put(filter.getId(), filter);
        }
    }

    @Override
    public boolean test(String filterId, DocumentModel doc) {
        if (!filters.containsKey(filterId)) {
            log.warn("No such filter: {}", filterId);
            return false;
        }

        ResultsFilter resultsFilter = filters.get(filterId);
        return resultsFilter.accept(doc);
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
