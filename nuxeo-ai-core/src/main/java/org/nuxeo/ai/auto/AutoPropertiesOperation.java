/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.auto;

import org.nuxeo.ai.auto.AutoService.AUTO_ACTION;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;

@Operation(id = AutoPropertiesOperation.ID, category = Constants.CAT_DOCUMENT, label = "Auto-calculate document properties")
public class AutoPropertiesOperation {

    public static final String ID = "AI.AutoProperties";

    @Context
    public CoreSession coreSession;

    @Context
    protected AutoService autoService;

    @Param(name = "document", description = "A document", required = false)
    protected DocumentModel documentModel;

    @Param(name = "autoAction", description = "Allowed values are FILL, CORRECT, ALL", required = false)
    protected String autoAction;

    @Param(name = "save", description = "Should the document be saved?", required = false)
    protected boolean save = false;

    @OperationMethod
    public DocumentModel run(DocumentModel doc) {
        AUTO_ACTION action = autoAction != null ? AUTO_ACTION.valueOf(autoAction) : AUTO_ACTION.ALL;
        autoService.calculateProperties(doc, action);

        if (save) {
            coreSession.saveDocument(doc);
        }
        return doc;
    }

    @OperationMethod
    public DocumentModel run(DocumentRef docRef) {
        DocumentModel docModel = coreSession.getDocument(docRef);
        return run(docModel);
    }

    @OperationMethod
    public DocumentModel run() {
        return run(documentModel);
    }

}
