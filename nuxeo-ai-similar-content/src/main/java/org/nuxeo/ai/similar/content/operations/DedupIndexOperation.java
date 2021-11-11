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

package org.nuxeo.ai.similar.content.operations;

import static java.util.Collections.singletonList;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.base64EncodeBlob;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.getPictureConversion;
import static org.nuxeo.ai.sdk.rest.Common.UID;
import static org.nuxeo.ai.sdk.rest.Common.XPATH_PARAM;
import static org.nuxeo.ai.similar.content.DedupConstants.DEDUPLICATION_FACET;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.sdk.objects.TensorInstances;
import org.nuxeo.ai.sdk.rest.client.API;
import org.nuxeo.ai.sdk.rest.client.InsightClient;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.blob.ManagedBlob;

/**
 * Index document(s) with a given xpath in dedup index on insight.
 */
@Operation(id = DedupIndexOperation.ID, category = "AICore", label = "Dedup Index Operation", description = "Index document(s) with a given xpath in dedup index on insight")
public class DedupIndexOperation {

    public static final String ID = "AICore.DedupIndexOperation";

    private static final Logger log = LogManager.getLogger(DedupIndexOperation.class);

    @Context
    protected CoreSession session;

    @Context
    protected CloudClient client;

    @Param(name = "xpath")
    protected String xpath;

    @OperationMethod
    public Response run(DocumentModel doc) throws IOException {
        DocumentModelListImpl docs = new DocumentModelListImpl(1);
        docs.add(doc);
        return run(docs);
    }

    @OperationMethod
    public Response run(DocumentModelList docs) throws IOException {
        if(docs == null || docs.isEmpty()){
            log.error("Documents list in input should not be empty - [xpath={}]", xpath);
            return Response.serverError().build();
        }

        InsightClient insightClient = client.getClient(session).orElse(null);
        if (!client.isAvailable(session) || insightClient==null) {
            log.error("Cannot access Insight Client - [docs={},xpath={}]", docs, xpath);
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        HashMap<String, Serializable> params = new HashMap<>();
        for(DocumentModel doc: docs){
            params.put(UID, doc.getId());
            params.put(XPATH_PARAM, xpath);
            TensorInstances instances = constructTensor(doc, (ManagedBlob) doc.getPropertyValue(xpath));
            Boolean result = insightClient.api(API.Dedup.INDEX).call(params, instances);
            if(Boolean.FALSE.equals(result)){
                log.error("Couldn't trigger dedup index - [docId={}, xpath={}]",doc.getId(), xpath);
            }else{
                addDeduplicationFacet(doc);
            }
        }
        return Response.ok().build();
    }

    protected void addDeduplicationFacet(DocumentModel doc) {
        List<Map<String, Object>> history = new ArrayList<>(1);
        doc.addFacet(DEDUPLICATION_FACET);
        Map<String, Object> entry = new HashMap<>();
        entry.put("xpath", xpath);
        entry.put("index", true);
        entry.put("date", new GregorianCalendar());
        history.add(entry);
        doc.setPropertyValue("dedup:history", (Serializable) history);
        session.saveDocument(doc);
    }

    protected TensorInstances constructTensor(DocumentModel doc, ManagedBlob blob) {
        if (blob == null) {
            return null;
        }
        Map<String, TensorInstances.Tensor> props = new HashMap<>();
        props.put(xpath, TensorInstances.Tensor.image(convert(getPictureConversion(doc, blob))));
        return new TensorInstances(doc.getId(), singletonList(props));
    }

    private String convert(ManagedBlob blob) {
        if (blob != null) {
            return base64EncodeBlob(blob);
        }
        return null;
    }
}
