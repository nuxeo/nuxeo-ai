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

package org.nuxeo.ai.similar.content.operation;

import static org.nuxeo.ai.similar.content.DedupConstants.CONF_DEDUPLICATION_CONFIGURATION;
import static org.nuxeo.ai.similar.content.DedupConstants.DEFAULT_CONFIGURATION;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.similar.content.services.SimilarContentService;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.api.Framework;

@Operation(id = RunIndexOperation.ID, label = "Run Index BAF", description = "Schedules an Index Action on Bulk Action Framework; allowed to be executed only by Administrators")
public class RunIndexOperation {

    private static final Logger log = LogManager.getLogger(RunIndexOperation.class);

    public static final String ID = "Insight.IndexRepository";

    @Context
    protected SimilarContentService scs;

    @Context
    protected CoreSession session;

    @Param(name = "reindex", required = false)
    protected boolean reindex = false;

    @OperationMethod
    public String run() {
        if (!session.getPrincipal().isAdministrator()) {
            log.warn("User {} is not authorised to run the {} operation.", session.getPrincipal().getActingUser(), ID);
            return null;
        }

        String configuration = Framework.getProperty(CONF_DEDUPLICATION_CONFIGURATION, DEFAULT_CONFIGURATION);
        String query = scs.getQuery(configuration);
        return scs.index(query, session.getPrincipal().getActingUser(), reindex);
    }
}
