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
package org.nuxeo.ai.similar.content.services;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.nuxeo.ai.sdk.rest.Common.UID;
import static org.nuxeo.ai.sdk.rest.Common.XPATH_PARAM;
import static org.nuxeo.ai.similar.content.DedupConstants.DEDUPLICATION_FACET;
import static org.nuxeo.ai.similar.content.DedupConstants.NOT_DUPLICATE_TAG;
import static org.nuxeo.ai.similar.content.pipelines.IndexAction.INDEX_ACTION_NAME;
import static org.nuxeo.ai.similar.content.utils.PictureUtils.resize;
import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.STATUS_PREFIX;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.bulk.BulkProgressStatus;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.sdk.objects.TensorInstances;
import org.nuxeo.ai.sdk.rest.client.API;
import org.nuxeo.ai.sdk.rest.client.InsightClient;
import org.nuxeo.ai.similar.content.configuration.DeduplicationDescriptor;
import org.nuxeo.ai.similar.content.configuration.OperationDescriptor;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ConcurrentUpdateException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.bulk.BulkCodecs;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.BulkServiceImpl;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueStoreProvider;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

public class SimilarServiceComponent extends DefaultComponent implements SimilarContentService {

    private static final Logger log = LogManager.getLogger(SimilarServiceComponent.class);

    public static final String DEDUPLICATION_CONFIG_XP = "configuration";

    public static final String DEDUPLICATION_OPERATION_XP = "operation";

    public static final String DEDUPLICATION_FACET_EXCLUSION_NXQL =
            " AND ecm:mixinType != " + NXQL.escapeString(DEDUPLICATION_FACET);

    public static final String DOCUMENT_INDEXED_EVENT = "documentIndexed";

    protected final Map<String, DeduplicationDescriptor> dedupDescriptors = new HashMap<>();

    protected String operationID = null;

    @Override
    public void registerContribution(Object contribution, String xp, ComponentInstance component) {
        if (DEDUPLICATION_CONFIG_XP.equals(xp)) {
            DeduplicationDescriptor desc = (DeduplicationDescriptor) contribution;
            dedupDescriptors.put(desc.getName(), desc);
        } else if (DEDUPLICATION_OPERATION_XP.equals(xp)) {
            OperationDescriptor desc = (OperationDescriptor) contribution;
            if (StringUtils.isNotEmpty(operationID)) {
                log.warn("Deduplication operation is already defined {}; There was an attempt to override it with {}",
                        operationID, desc.getId());
            }

            operationID = desc.getId();
        }
    }

    @Override
    public boolean test(String config, DocumentModel doc) {
        if (!dedupDescriptors.containsKey(config)) {
            log.warn("No such configuration: {}", config);
            return false;
        }

        return Stream.of(dedupDescriptors.get(config).getFilters()).allMatch(filter -> filter.accept(doc));
    }

    @Override
    public boolean anyMatch(DocumentModel doc) {
        return dedupDescriptors.values()
                               .stream()
                               .anyMatch(d -> Arrays.stream(d.getFilters()).allMatch(filter -> filter.accept(doc)));
    }

    @Override
    public String getOperationID() {
        return operationID;
    }

    @Override
    public String index(CoreSession session, String query, boolean reindex) throws IOException {
        BulkProgressStatus status = getStatus();
        boolean alreadyRunning = status != null && isActive(status);
        if (alreadyRunning) {
            throw new ConcurrentUpdateException("Only one deduplication index action is allowed at a time");
        }

        if (!reindex && !query.contains(DEDUPLICATION_FACET_EXCLUSION_NXQL)) {
            query += DEDUPLICATION_FACET_EXCLUSION_NXQL;
        }

        if (reindex) {
            log.info("Dropping Index at Insight");
            if (!drop(session)) {
                throw new NuxeoException("Could not drop the index; aborting Index BAF");
            }
            log.debug("Index has been dropped");
        }

        BulkCommand command = new BulkCommand.Builder(INDEX_ACTION_NAME, query).user(
                session.getPrincipal().getActingUser()).repository(session.getRepositoryName()).build();
        return Framework.getService(BulkService.class).submit(command);
    }

    @Nullable
    @Override
    public BulkProgressStatus getStatus() {
        return getStatus(null);
    }

    @Override
    public BulkProgressStatus getStatus(String id) {
        BulkServiceImpl bs = (BulkServiceImpl) Framework.getService(BulkService.class);
        KeyValueStoreProvider kv = (KeyValueStoreProvider) bs.getKvStore();
        return kv.keyStream(STATUS_PREFIX)
                 .map(kv::get)
                 .map(BulkCodecs.getStatusCodec()::decode)
                 .filter(status -> INDEX_ACTION_NAME.equals(status.getAction()))
                 .filter(status -> id == null || status.getId().equals(id))
                 .sorted(Comparator.comparing(BulkStatus::getState))
                 .map(BulkProgressStatus::new)
                 .findFirst()
                 .orElse(null);
    }

    @Override
    public boolean index(DocumentModel doc, String xpath) throws IOException {
        CoreSession session = doc.getCoreSession();
        InsightClient client = getInsightClient(session);

        HashMap<String, Serializable> params = new HashMap<>();
        params.put(UID, doc.getId());
        params.put(XPATH_PARAM, xpath);

        Serializable value = doc.getPropertyValue(xpath);
        if (value == null) {
            log.warn("Cannot index Document {} with value at xpath {} = null", doc.getId(), xpath);
            return false;
        }

        TensorInstances instances = constructTensor(doc, xpath);
        Boolean result = client.api(API.Dedup.INDEX).call(params, instances);
        if (Boolean.FALSE.equals(result)) {
            log.error("Couldn't trigger dedup index - [docId={}, xpath={}]", doc.getId(), xpath);
            return false;
        } else {
            addDeduplicationFacet(session, doc, xpath);
            fireEvent(session, doc);
            return true;
        }
    }

    @Override
    public List<DocumentModel> findSimilar(CoreSession session, DocumentModel doc, String xpath) throws IOException {
        InsightClient client = getInsightClient(session);
        Map<String, Serializable> parameters = new HashMap<>();
        parameters.put(UID, doc.getId());
        parameters.put(XPATH_PARAM, xpath);

        List<String> ids = client.api(API.Dedup.FIND).call(parameters, null);
        if (ids == null || ids.isEmpty()) {
            log.debug("No similar documents found");
            return emptyList();
        }

        List<DocumentRef> refs = ids.stream().map(IdRef::new).filter(session::exists).collect(Collectors.toList());
        if (refs.size() != ids.size()) {
            log.warn("Deduplication found some nonexistent document, consider reindexing");
        }

        return session.getDocuments(refs.toArray(new DocumentRef[] {}));
    }

    @Override
    public List<DocumentModel> findSimilar(CoreSession session, Blob blob, String xpath) throws IOException {
        InsightClient client = getInsightClient(session);
        Map<String, Serializable> parameters = new HashMap<>();
        parameters.put(XPATH_PARAM, xpath);
        TensorInstances tensor = constructTensor(blob, xpath);

        List<String> ids = client.api(API.Dedup.FIND).call(parameters, tensor);
        return resolveDocuments(session, ids);
    }

    @Override
    public boolean delete(DocumentModel doc, String xpath) throws IOException {
        InsightClient client = getInsightClient(doc.getCoreSession());

        HashMap<String, Serializable> params = new HashMap<>();
        params.put(UID, doc.getId());
        if (StringUtils.isNotEmpty(xpath)) {
            params.put(XPATH_PARAM, xpath);
        }

        Boolean result = client.api(API.Dedup.DELETE).call(params, "{}");
        if (Boolean.FALSE.equals(result)) {
            log.error("Couldn't trigger dedup index - [docId={}, xpath={}]", doc.getId(), xpath);
            return false;
        }

        return true;
    }

    @Override
    public boolean drop(CoreSession session) throws IOException {
        InsightClient client = getInsightClient(session);
        return Boolean.TRUE.equals(client.api(API.Dedup.DROP).call());
    }

    protected void addDeduplicationFacet(CoreSession session, DocumentModel doc, String xpath) {
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

    protected void fireEvent(CoreSession session, DocumentModel doc) {
        DocumentEventContext ctx = new DocumentEventContext(session, session.getPrincipal(), doc);
        EventService eventService = Framework.getService(EventService.class);
        eventService.fireEvent(DOCUMENT_INDEXED_EVENT, ctx);
    }

    protected InsightClient getInsightClient(CoreSession session) {
        InsightClient client = getClient(session);
        if (client == null) {
            throw new NuxeoException(
                    "Could not obtain Insight Client for user " + session.getPrincipal().getActingUser());
        }

        return client;
    }

    protected List<DocumentModel> resolveDocuments(CoreSession session, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            log.debug("No similar documents found");
            return emptyList();
        }

        List<DocumentRef> refs = ids.stream().map(IdRef::new).filter(session::exists).collect(Collectors.toList());
        if (refs.size() != ids.size()) {
            log.warn("Deduplication found some nonexistent document, consider reindexing");
        }

        return session.getDocuments(refs.toArray(new DocumentRef[] {}))
                      .stream()
                      .filter(this::tagNotDuplicateEmpty)
                      .collect(Collectors.toList());
    }

    protected boolean tagNotDuplicateEmpty(DocumentModel doc) {
        @SuppressWarnings("unchecked")
        List<Map<String, Serializable>> tags = (List<Map<String, Serializable>>) doc.getPropertyValue("nxtag:tags");
        if (tags == null || tags.isEmpty()) {
            return true;
        }

        for (Map<String, Serializable> tag : tags) {
            if (tag.containsKey("label") && tag.get("label").equals(NOT_DUPLICATE_TAG)) {
                return false;
            }
        }

        return true;
    }

    @Nullable
    protected TensorInstances constructTensor(Blob blob, String xpath) {
        if (blob == null) {
            return null;
        }

        Map<String, TensorInstances.Tensor> props = new HashMap<>();
        props.put(xpath, TensorInstances.Tensor.image(resize(blob)));
        return new TensorInstances(null, singletonList(props));
    }

    @Nullable
    protected TensorInstances constructTensor(DocumentModel doc, String xpath) {
        Blob blob = (Blob) doc.getPropertyValue(xpath);
        if (blob == null) {
            return null;
        }

        Map<String, TensorInstances.Tensor> props = new HashMap<>();
        props.put(xpath, TensorInstances.Tensor.image(resize(blob)));
        return new TensorInstances(doc.getId(), singletonList(props));
    }

    @Nullable
    protected InsightClient getClient(CoreSession session) {
        CloudClient client = Framework.getService(CloudClient.class);
        InsightClient insightClient = client.getClient(session).orElse(null);
        if (!client.isAvailable(session) || insightClient == null) {
            return null;
        }

        return insightClient;
    }

    @Override
    public String getQuery(String name) {
        return dedupDescriptors.get(name).getQuery();
    }

    @Override
    public String getXPath(String name) {
        return dedupDescriptors.get(name).getXPath();
    }

    protected boolean isActive(BulkProgressStatus status) {
        return status.getState().equals(BulkStatus.State.RUNNING.name()) && status.getState()
                                                                                  .equals(BulkStatus.State.SCHEDULED.name())
                && status.getState().equals(BulkStatus.State.SCROLLING_RUNNING.name());
    }
}
