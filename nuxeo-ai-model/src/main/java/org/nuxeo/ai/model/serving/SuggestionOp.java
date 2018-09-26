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
package org.nuxeo.ai.model.serving;

import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

import java.io.IOException;
import java.util.List;

import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;

/**
 * Suggests metadata for the specified document.
 */
@Operation(id = SuggestionOp.ID, category = Constants.CAT_DOCUMENT, label = "Ask for a suggestion.",
        description = "Calls intelligent services on the provided document and returns suggested metadata.")
public class SuggestionOp {

    public static final String ID = "Document.AISuggestion";

    public static final String EMPTY_JSON_LIST = "[]";

    @Context
    public CoreSession coreSession;

    @Context
    protected ModelServingService modelServingService;

    @Param(name = "document", description = "A document", required = false)
    protected DocumentModel documentModel;

    @OperationMethod
    public Blob run(DocumentModel doc) throws IOException {

        List<EnrichmentMetadata> suggestions;
        if (doc == null || (suggestions = modelServingService.predict(doc)) == null || suggestions.isEmpty()) {
            return Blobs.createJSONBlob(EMPTY_JSON_LIST);
        }
        return Blobs.createJSONBlob(MAPPER.writeValueAsString(suggestions));
    }

    @OperationMethod
    public Blob run(DocumentRef docRef) throws IOException {
        DocumentModel docModel = coreSession.getDocument(docRef);
        return run(docModel);
    }

    @OperationMethod
    public Blob run() throws IOException {
        return run(documentModel);
    }
}
