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
 *     Gethin James
 */
package org.nuxeo.runtime.stream.pipes.filters;

import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.stream.pipes.streams.Initializable;

/**
 * A Document Facet filter.
 * Check that none match the excluded facets and ANY match the included facets
 */
public class FacetFilter implements Filter.DocumentFilter, Initializable {

    protected List<String> includedFacets;
    protected List<String> excludedFacets;

    @Override
    public void init(Map<String, String> options) {
        includedFacets = propsList(options.get("includedFacets"));
        excludedFacets = propsList(options.getOrDefault("excludedFacets", "Folderish"));
    }

    /**
     * A predicate that check that none match the excluded facets and ANY match the included facets
     */
    @Override
    public boolean test(DocumentModel doc) {
        return excludedFacets.stream().noneMatch(doc::hasFacet) &&
                (includedFacets.isEmpty() || includedFacets.stream().anyMatch(doc::hasFacet));
    }

}