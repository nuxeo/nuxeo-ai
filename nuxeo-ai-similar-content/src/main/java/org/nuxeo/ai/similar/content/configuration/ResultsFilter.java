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

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.platform.actions.DefaultActionFilter;

/**
 * File was extracted from {@link DefaultActionFilter} with some minor changes to fit the requirements
 */
@XObject("filter")
public class ResultsFilter implements Cloneable, Serializable {

    private static final long serialVersionUID = 5879462416904401002L;

    private static final Logger log = LogManager.getLogger(ResultsFilter.class);

    public static final String PRECOMPUTED_KEY = "PrecomputedFilters";

    @XNode("@id")
    protected String id;

    @XNode("@append")
    protected boolean append;

    @XNodeList(value = "rule", type = String[].class, componentType = ResultsRule.class)
    protected ResultsRule[] rules;

    public ResultsFilter() {
        this(null, null, false);
    }

    public ResultsFilter(String id, ResultsRule[] rules) {
        this(id, rules, false);
    }

    public ResultsFilter(String id, ResultsRule[] rules, boolean append) {
        this.id = id;
        this.rules = rules;
        this.append = append;
    }

    public String getId() {
        return id;
    }

    public ResultsRule[] getRules() {
        return rules;
    }

    public boolean accept(DocumentModel doc) {
        // no context: reject
        if (doc == null) {
            log.debug("#accept: no context available: action filtered");
            return false;
        }
        // no rule: accept
        if (rules == null || rules.length == 0) {
            return true;
        }

        boolean existsGrantRule = false;
        boolean grantApply = false;
        for (ResultsRule rule : rules) {
            boolean ruleApplies = checkRule(rule, doc);
            if (!rule.grant) {
                if (ruleApplies) {
                    log.debug("#accept: denying rule applies => action filtered");
                    return false;
                }
            } else {
                existsGrantRule = true;
                if (ruleApplies) {
                    grantApply = true;
                }
            }
        }

        if (existsGrantRule) {
            if (log.isDebugEnabled()) {
                if (grantApply) {
                    log.debug("#accept: granting rule applies, action not filtered");
                } else {
                    log.debug("#accept: granting rule applies, action filtered");
                }
            }

            return grantApply;
        }
        // there is no allow rule, and none of deny rules applies
        return true;
    }

    protected final boolean checkRule(ResultsRule rule, DocumentModel doc) {
        if (log.isDebugEnabled()) {
            log.debug("#checkRule: checking rule {}", rule);
        }

        // TODO: for optimization reasons cache should be used; see org.nuxeo.ecm.platform.actions.DefaultActionFilter
        return (rule.facets == null || rule.facets.length == 0 || checkFacets(doc, rule.facets)) && (rule.types == null
                || rule.types.length == 0 || checkTypes(doc, rule.types)) && (rule.schemas == null
                || rule.schemas.length == 0 || checkSchemas(doc, rule.schemas)) && (rule.permissions == null
                || rule.permissions.length == 0 || checkPermissions(doc, rule.permissions)) && (rule.groups == null
                || rule.groups.length == 0 || checkGroups(doc, rule.groups));
    }

    /**
     * Returns true if document has one of the given facets, else false.
     *
     * @return true if document has one of the given facets, else false.
     */
    protected final boolean checkFacets(DocumentModel doc, String[] facets) {
        if (doc == null) {
            return false;
        }

        for (String facet : facets) {
            if (doc.hasFacet(facet)) {
                if (log.isDebugEnabled()) {
                    log.debug("#checkFacets: return true for facet {}", facet);
                }
                return true;
            }
        }

        log.debug("#checkFacets: return false");
        return false;
    }

    /**
     * Returns true if given document has one of the permissions, else false.
     * <p>
     * If no document is found, return true only if principal is a manager.
     *
     * @return true if given document has one of the given permissions, else false
     */
    protected final boolean checkPermissions(DocumentModel doc, String[] permissions) {
        if (doc == null) {
            log.debug("#checkPermissions: doc and user are null => return false");
            return false;
        }
        // check rights on doc
        CoreSession session = doc.getCoreSession();
        if (session == null) {
            log.debug("#checkPermissions: no core session => return false");
            return false;
        }

        for (String permission : permissions) {
            if (session.hasPermission(doc.getRef(), permission)) {
                log.debug("#checkPermissions: return true for permission {}", permission);
                return true;
            }
        }

        log.debug("#checkPermissions: return false");
        return false;
    }

    protected final boolean checkGroups(DocumentModel doc, String[] groups) {
        if (doc == null) {
            log.debug("#checkPermissions: doc and user are null => return false");
            return false;
        }
        // check rights on doc
        CoreSession session = doc.getCoreSession();
        if (session == null) {
            log.debug("#checkPermissions: no core session => return false");
            return false;
        }

        NuxeoPrincipal principal = session.getPrincipal();
        for (String group : groups) {
            if (principal.isMemberOf(group)) {
                if (log.isDebugEnabled()) {
                    log.debug("#checkGroups: return true for group {}", group);
                }
                return true;
            }
        }

        log.debug("#checkGroups: return false");
        return false;
    }

    /**
     * Returns true if document type is one of the given types, else false.
     * <p>
     * If document is null, consider context is the server and return true if 'Server' is in the list.
     *
     * @return true if document type is one of the given types, else false.
     */
    protected final boolean checkTypes(DocumentModel doc, String[] types) {
        String docType;
        if (doc == null) {
            // consider we're on the Server root
            docType = "Root";
        } else {
            docType = doc.getType();
        }

        for (String type : types) {
            if (type.equals(docType)) {
                if (log.isDebugEnabled()) {
                    log.debug("#checkTypes: return true for type {}", docType);
                }

                return true;
            }
        }

        log.debug("#checkTypes: return false");
        return false;
    }

    /**
     * Returns true if document has one of the given schemas, else false.
     *
     * @return true if document has one of the given schemas, else false
     */
    protected final boolean checkSchemas(DocumentModel doc, String[] schemas) {
        if (doc == null) {
            if (log.isDebugEnabled()) {
                log.debug("#checkSchemas: no doc => return false");
            }

            return false;
        }

        for (String schema : schemas) {
            if (doc.hasSchema(schema)) {
                if (log.isDebugEnabled()) {
                    log.debug("#checkSchemas: return true for schema {}", schema);
                }

                return true;
            }
        }

        log.debug("#checkSchemas: return false");
        return false;
    }

    public boolean getAppend() {
        return append;
    }

    public void setAppend(boolean append) {
        this.append = append;
    }

    @Override
    public ResultsFilter clone() throws CloneNotSupportedException {
        super.clone();
        ResultsFilter clone = new ResultsFilter();
        clone.id = id;
        clone.append = append;
        if (rules != null) {
            clone.rules = new ResultsRule[rules.length];
            for (int i = 0; i < rules.length; i++) {
                clone.rules[i] = rules[i].clone();
            }
        }

        return clone;
    }

    /**
     * Equals method added to handle hot reload of inner filters, see NXP-9677
     *
     * @since 5.6
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ResultsFilter)) {
            return false;
        }
        final ResultsFilter o = (ResultsFilter) obj;
        String objId = o.getId();
        if (objId == null && !(this.id == null)) {
            return false;
        }
        if (this.id == null && !(objId == null)) {
            return false;
        }
        if (objId != null && !objId.equals(this.id)) {
            return false;
        }
        boolean append = o.getAppend();
        if (!append == this.append) {
            return false;
        }
        ResultsRule[] objRules = o.getRules();
        if (objRules == null && !(this.rules == null)) {
            return false;
        }
        if (this.rules == null && !(objRules == null)) {
            return false;
        }
        if (objRules != null) {
            if (objRules.length != this.rules.length) {
                return false;
            }
            for (int i = 0; i < objRules.length; i++) {
                if (objRules[i] == null && (!(this.rules[i] == null))) {
                    return false;
                }
                if (this.rules[i] == null && (!(objRules[i] == null))) {
                    return false;
                }
                if (!objRules[i].equals(this.rules[i])) {
                    return false;
                }
            }
        }
        return true;
    }
}
