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
import static java.util.Collections.singletonList;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.AI_BLOB_MAX_SIZE_CONF_VAR;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.AI_BLOB_MAX_SIZE_VALUE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.FILE_CONTENT;
import static org.nuxeo.ai.sdk.rest.Common.UID;
import static org.nuxeo.ai.sdk.rest.Common.XPATH_PARAM;
import static org.nuxeo.ai.similar.content.utils.PictureUtils.resize;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.sdk.objects.TensorInstances;
import org.nuxeo.ai.sdk.rest.client.API;
import org.nuxeo.ai.sdk.rest.client.InsightClient;
import org.nuxeo.ai.similar.content.services.SimilarContentService;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.runtime.api.Framework;

@Operation(id = FindSimilar.ID, category = "Insight", label = "Find duplicate documents")
public class FindSimilar {

    private static final Logger log = LogManager.getLogger(FindSimilar.class);

    public static final String ID = "Insight.FindSimilar";

    @Context
    protected CoreSession session;

    @Context
    protected CloudClient client;

    @Context
    protected SimilarContentService scs;

    @Param(name = "xpath", required = false)
    protected String xpath = FILE_CONTENT;

    @Param(name = "max", required = false)
    protected long max;

    @OperationMethod
    public List<DocumentModel> run(DocumentModel doc) throws OperationException {
        if (!scs.anyMatch(doc)) {
            log.debug("None dedup filters fit document {}", doc.getId());
            return emptyList();
        }

        List<String> ids = findSimilarIds(doc, null);
        return resolveDocuments(ids);
    }

    @OperationMethod
    public List<DocumentModel> run(Blob blob) throws OperationException {
        if (blob.getLength() >= Long.parseLong(
                Framework.getProperty(AI_BLOB_MAX_SIZE_CONF_VAR, AI_BLOB_MAX_SIZE_VALUE))) {
            throw new OperationException("Blob is too large; size = " + blob.getLength());
        }

        List<String> ids = findSimilarIds(null, blob);
        return resolveDocuments(ids);
    }

    protected List<String> findSimilarIds(@Nullable DocumentModel doc, @Nullable Blob blob) throws OperationException {
        Optional<InsightClient> client = this.client.getClient(session);
        if (client.isEmpty()) {
            throw new OperationException(
                    "Could not obtain Insight Client for user " + session.getPrincipal().getActingUser());
        }

        String id = doc == null ? null : doc.getId();
        TensorInstances tensor = constructTensor(blob, id);

        Map<String, Serializable> parameters = new HashMap<>();
        parameters.put(UID, id);
        parameters.put(XPATH_PARAM, xpath);

        try {
            InsightClient insight = client.get();
            return insight.api(API.Dedup.FIND).call(parameters, tensor);
        } catch (IOException e) {
            throw new OperationException("Could not call Find API", e);
        }
    }

    protected List<DocumentModel> resolveDocuments(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            log.debug("No similar documents found");
            return emptyList();
        }

        List<DocumentRef> refs = ids.stream()
                                    .map(IdRef::new)
                                    .filter(ref -> session.exists(ref))
                                    .collect(Collectors.toList());
        if (refs.size() != ids.size()) {
            log.warn("Deduplication found some nonexistent document, consider reindexing");
        }

        return session.getDocuments(refs.toArray(DocumentRef[]::new));
    }

    protected TensorInstances constructTensor(Blob blob, String id) {
        if (blob == null) {
            return null;
        }

        Map<String, TensorInstances.Tensor> props = new HashMap<>();
        props.put(xpath, TensorInstances.Tensor.image(resize(blob)));
        return new TensorInstances(id, singletonList(props));
    }
}
