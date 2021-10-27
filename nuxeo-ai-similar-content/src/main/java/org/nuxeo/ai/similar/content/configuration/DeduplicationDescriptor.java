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

package org.nuxeo.ai.similar.content.configuration;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.runtime.model.Descriptor;

@XObject("deduplication")
public class DeduplicationDescriptor implements Descriptor {

    public static final String DEFAULT_QUERY = "SELECT * FROM Document WHERE ecm:mixinType = 'Picture' AND ecm:tag NOT IN ('not_duplicate')";

    public static final String DEFAULT_XPATH = "file:content";

    @XNode("@name")
    protected String name;

    @XNode("@query")
    protected String query = DEFAULT_QUERY;

    @XNode("xpath")
    protected String xpath = DEFAULT_XPATH;

    @XNodeList(value = "filter", type = String[].class, componentType = ResultsFilter.class)
    protected ResultsFilter[] filters;

    @Override
    public String getId() {
        return name;
    }

    public String getName() {
        return name;
    }

    public String getQuery() {
        return query;
    }

    public String getXPath() {
        return xpath;
    }

    public ResultsFilter[] getFilters() {
        return filters;
    }
}
