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
package org.nuxeo.ai.enrichment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.events.DocEventToStream;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;

/**
 * Calls an enrichment service.
 */
@Operation(id = EnrichmentOp.ID, category = Constants.CAT_DOCUMENT, label = "Directly call an enrichment service",
        description = "Calls an enrichment service on the provided document(s)")
public class EnrichmentOp {

    public static final String ID = "AI.Enrichment";

    private static final Log log = LogFactory.getLog(EnrichmentOp.class);

    @Context
    protected OperationContext ctx;

    @Context
    protected CoreSession session;

    @Context
    protected AIComponent aiComponent;

    @Param(name = "enrichmentName", description = "The name of the enrichment service to call")
    protected String enrichmentName;

    @Param(name = "blobProperties", required = false)
    protected StringList blobProperties;

    @Param(name = "textProperties", required = false)
    protected StringList textProperties;

    @Param(name = "outputVariable", description = "The key of the context output variable. "
            + "The output variable is a list of EnrichmentMetadata objects. ")
    protected String outputVariable;

    @OperationMethod
    public DocumentModel run(DocumentModel doc) {

        if (doc == null) {
            return null;
        }
        DocumentModelListImpl docs = new DocumentModelListImpl(1);
        docs.add(doc);
        run(docs);
        return doc;
    }

    @OperationMethod
    public DocumentModelList run(DocumentModelList docs) {
        List<AIMetadata> results = new ArrayList<>();
        if (!docs.isEmpty()) {

            EnrichmentProvider service = aiComponent.getEnrichmentProvider(enrichmentName);
            if (service == null) {
                throw new NuxeoException("Unknown enrichment service " + enrichmentName);
            }
            if (blobProperties == null && textProperties == null) {
                throw new NuxeoException("You must specify either a blob or text property to use");
            }
            DocEventToStream docEventToStream = new DocEventToStream(blobProperties, textProperties, null);

            docs.forEach(documentModel -> {
                Collection<BlobTextFromDocument> blobTexts = docEventToStream.docSerialize(documentModel);
                blobTexts.forEach(b -> {
                    Collection<EnrichmentMetadata> result = null;
                    try {
                        result = service.enrich(b);
                    } catch (NuxeoException e) {
                        log.warn(String.format("Call to enrichment service %s failed.", enrichmentName), e);
                    }
                    if (result != null) {
                        results.addAll(result);
                    }
                });
            });

            ctx.put(outputVariable, results);
        }
        return docs;
    }

}
