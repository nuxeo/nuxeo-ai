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
package org.nuxeo.ai.pipes.filters;

import java.util.List;
import java.util.Map;
import org.nuxeo.ai.pipes.streams.Initializable;
import org.nuxeo.ecm.core.api.DocumentModel;

public class NoVersionFilter implements Filter.DocumentFilter, Initializable {

    protected List<String> facets;

    protected boolean excluded;

    @Override
    public void init(Map<String, String> options) {
        /* NOP */
    }

    /**
     * A predicate that check that none match the excluded facets and ANY match the included facets
     */
    @Override
    public boolean test(DocumentModel doc) {
        return !doc.isVersion();
    }

}
