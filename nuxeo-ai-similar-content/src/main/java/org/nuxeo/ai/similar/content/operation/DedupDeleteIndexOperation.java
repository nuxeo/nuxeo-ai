/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo
 */

package org.nuxeo.ai.similar.content.operation;

import java.io.IOException;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.similar.content.services.SimilarContentService;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;

/**
 * Remove document(s) with an optional xpath from dedup index on insight.
 */
@Operation(id = DedupDeleteIndexOperation.ID, category = "AICore", label = "Delete Dedup Index Operation", description = "Remove document(s) with an optional xpath from dedup index on insight")
public class DedupDeleteIndexOperation {

    private static final Logger log = LogManager.getLogger(DedupDeleteIndexOperation.class);

    public static final String ID = "AICore.DedupDeleteIndexOperation";

    @Context
    protected CoreSession session;

    @Context
    protected CloudClient client;

    @Context
    protected SimilarContentService scs;

    @Param(name = "xpath", required = false)
    protected String xpath;

    @OperationMethod
    public Response run(DocumentModel doc) {
        DocumentModelListImpl docs = new DocumentModelListImpl(1);
        docs.add(doc);
        return run(docs);
    }

    @OperationMethod
    public Response run(DocumentModelList docs) {
        if (docs == null || docs.isEmpty()) {
            log.error("Documents list in input should not be empty - [xpath={}]", xpath);
            return Response.serverError().build();
        }

        return docs.stream().map(doc -> {
            try {
                return scs.delete(doc, xpath);
            } catch (IOException e) {
                log.error(e);
                return false;
            }
        }).anyMatch(flag -> !flag) ? Response.serverError().build() : Response.ok().build();
    }

}
