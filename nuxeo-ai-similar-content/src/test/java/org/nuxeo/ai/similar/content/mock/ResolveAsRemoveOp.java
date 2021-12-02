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

package org.nuxeo.ai.similar.content.mock;

import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;

@Operation(id = ResolveAsRemoveOp.ID, label = "Test operation that removes duplicates")
public class ResolveAsRemoveOp {

    public static final String ID = "Test.ResolveAsRemove";

    @Param(name = "similar")
    protected Set<Pair<String, String>> similar;

    @Param(name = "xpath")
    protected String xpath;

    @Context
    protected CoreSession session;

    @OperationMethod
    public void resolve(DocumentRef doc) {
        for (Pair<String, String> pair : similar) {
            String uuid = pair.getKey();
            session.removeDocument(new IdRef(uuid));
        }
    }
}
