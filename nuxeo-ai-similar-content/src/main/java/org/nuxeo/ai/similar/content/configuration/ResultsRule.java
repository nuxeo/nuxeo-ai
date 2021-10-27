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
 *    Nuxeo
 */
package org.nuxeo.ai.similar.content.configuration;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.platform.actions.FilterRule;

/**
 * Class was extracted from {@link FilterRule} with some minor changes to fit the requirements
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 * @author <a href="mailto:rspivak@nuxeo.com">Ruslan Spivak</a>
 * @author <a href="mailto:rspivak@nuxeo.com">Andrei Nechaev</a>
 */
@XObject("rule")
public class ResultsRule {

    protected String cacheKey;

    @XNodeList(value = "permission", type = String[].class, componentType = String.class)
    protected String[] permissions;

    @XNodeList(value = "facet", type = String[].class, componentType = String.class)
    protected String[] facets;

    @XNodeList(value = "type", type = String[].class, componentType = String.class)
    protected String[] types;

    @XNodeList(value = "schema", type = String[].class, componentType = String.class)
    protected String[] schemas;

    @XNodeList(value = "group", type = String[].class, componentType = String.class)
    protected String[] groups;

    @XNode(value = "@grant")
    boolean grant = false;

    public ResultsRule() {
    }

    public ResultsRule(boolean grant, String[] permissions, String[] facets, String[] types, String[] schemas) {
        this.grant = grant;
        this.permissions = permissions;
        this.facets = facets;
        this.types = types;
        this.schemas = schemas;
    }

    public String getCacheKey() {
        if (cacheKey == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("grant:");
            sb.append(grant);
            if (permissions != null && permissions.length > 0) {
                sb.append(":permissions:");
                for (String perm : permissions) {
                    sb.append(perm);
                    sb.append(",");
                }
            }

            if (facets != null && facets.length > 0) {
                sb.append(":facets:");
                for (String facet : facets) {
                    sb.append(facet);
                    sb.append(",");
                }
            }

            if (types != null && types.length > 0) {
                sb.append(":types:");
                for (String typ : types) {
                    sb.append(typ);
                    sb.append(",");
                }
            }

            if (schemas != null && schemas.length > 0) {
                sb.append(":schemas:");
                for (String schem : schemas) {
                    sb.append(schem);
                    sb.append(",");
                }
            }

            if (groups != null && groups.length > 0) {
                sb.append(":groups:");
                for (String group : groups) {
                    sb.append(group);
                    sb.append(",");
                }
            }
            cacheKey = sb.toString();
        }

        return cacheKey;
    }

    @Override
    public String toString() {
        return getCacheKey();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof ResultsRule)) {
            return false;
        }
        return getCacheKey().equals(((ResultsRule) obj).getCacheKey());
    }

    @Override
    public int hashCode() {
        return getCacheKey().hashCode();
    }

    @Override
    public ResultsRule clone() throws CloneNotSupportedException {
        super.clone();
        ResultsRule clone = new ResultsRule();
        clone.grant = grant;
        if (permissions != null) {
            clone.permissions = permissions.clone();
        }

        if (facets != null) {
            clone.facets = facets.clone();
        }

        if (types != null) {
            clone.types = types.clone();
        }

        if (schemas != null) {
            clone.schemas = schemas.clone();
        }

        if (groups != null) {
            clone.groups = groups.clone();
        }

        clone.cacheKey = cacheKey;
        return clone;
    }

}
