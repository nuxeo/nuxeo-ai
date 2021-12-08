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

import static java.util.Collections.emptyList;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.AI_BLOB_MAX_SIZE_CONF_VAR;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.AI_BLOB_MAX_SIZE_VALUE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.FILE_CONTENT;

import java.io.IOException;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.similar.content.services.SimilarContentService;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.api.Framework;

@Operation(id = FindSimilar.ID, category = "AI", label = "Find duplicate documents")
public class FindSimilar {

    private static final Logger log = LogManager.getLogger(FindSimilar.class);

    public static final String ID = "AI.DeduplicationFindSimilar";

    @Context
    protected CoreSession session;

    @Context
    protected SimilarContentService scs;

    @Param(name = "xpath", required = false)
    protected String xpath = FILE_CONTENT;

    @OperationMethod
    public List<DocumentModel> run(DocumentModel doc) throws IOException {
        if (!scs.anyMatch(doc)) {
            log.debug("None dedup filters fit document {}", doc.getId());
            return emptyList();
        }

        return scs.findSimilar(session, doc, xpath);
    }

    @OperationMethod
    public List<DocumentModel> run(Blob blob) throws OperationException, IOException {
        if (blob.getLength() >= Long.parseLong(
                Framework.getProperty(AI_BLOB_MAX_SIZE_CONF_VAR, AI_BLOB_MAX_SIZE_VALUE))) {
            throw new OperationException("Blob is too large; size = " + blob.getLength());
        }

        return scs.findSimilar(session, blob, xpath);
    }
}
