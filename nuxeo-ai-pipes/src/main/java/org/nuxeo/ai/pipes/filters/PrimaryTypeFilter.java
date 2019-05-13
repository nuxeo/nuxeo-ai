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
package org.nuxeo.ai.pipes.filters;

import java.util.Map;
import org.nuxeo.ai.pipes.streams.Initializable;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * A Document type filter.
 * Check that the document is of the specified type.
 */
public class PrimaryTypeFilter implements Filter.DocumentFilter, Initializable {

    protected String isType;

    @Override
    public void init(Map<String, String> options) {
        isType = options.get("isType");
    }

    /**
     * A predicate that checks that the document is of the specified type.
     */
    @Override
    public boolean test(DocumentModel doc) {
        return doc.getType().equals(isType);
    }

}
