/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.listeners;

import static org.nuxeo.ai.bulk.ExportInitComputation.DEFAULT_SPLIT;
import static org.nuxeo.ai.model.export.CorpusDelta.CORPORA_ID_PARAM;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;
import static org.nuxeo.ecm.core.query.sql.model.Operator.AND;
import static org.nuxeo.ecm.core.query.sql.model.Operator.EQ;
import static org.nuxeo.ecm.core.query.sql.model.Operator.GT;
import static org.nuxeo.ecm.core.storage.BaseDocument.DC_MODIFIED;

import java.io.IOException;
import java.io.Serializable;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.model.export.CorpusDelta;
import org.nuxeo.ai.model.export.DatasetExportService;
import org.nuxeo.ai.utils.DateUtils;
import org.nuxeo.ecm.core.api.CloseableCoreSession;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.query.sql.SQLQueryParser;
import org.nuxeo.ecm.core.query.sql.model.DateLiteral;
import org.nuxeo.ecm.core.query.sql.model.IntegerLiteral;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.SQLQuery;
import org.nuxeo.ecm.core.query.sql.model.WhereClause;
import org.nuxeo.runtime.api.Framework;

/**
 * Asynchronous listener acting upon `startContinuousExport` event Performs REST Calls via {@link CloudClient} to obtain
 * all AI models and their corpora. The processing includes NXQL query modification for excluding previously exported
 * documents {@link DatasetExportService} will be called if and only if minimum documents were found in the repository
 */
public class ContinuousExportListener implements PostCommitEventListener {

    private static final Logger log = LogManager.getLogger(ContinuousExportListener.class);

    private static final String IS_VERSION_PROP = "ecm:isVersion";

    private static final String ENTRIES_KEY = "entries";

    private static final String DEFAULT_REPO = "default";

    public static final String START_CONTINUOUS_EXPORT = "startContinuousExport";

    public static final String FORCE_EXPORT = "forceContinuousExport";

    public static final String NUXEO_AI_CONTINUOUS_EXPORT_ENABLE = "nuxeo.ai.continuous.export.enable";

    private static final Predicate NOT_VERSION_PRED = new Predicate(new Reference(IS_VERSION_PROP), EQ,
            new IntegerLiteral(0));

    @Override
    public void handleEvent(EventBundle eb) {
        if (eb == null || eb.isEmpty()) {
            return;
        }

        if (!Boolean.parseBoolean(Framework.getProperty(NUXEO_AI_CONTINUOUS_EXPORT_ENABLE))) {
            EventContext eventContext = eb.peek().getContext();
            if (!eventContext.hasProperty(FORCE_EXPORT)) {
                return;
            }
        }

        CloudClient client = Framework.getService(CloudClient.class);

        List<String> uids = getModelIds(client);
        if (uids.isEmpty()) {
            return;
        }

        String repository = getRepositoryName(eb);
        initiateExport(client, uids, repository);
    }

    /**
     * @param client {@link CloudClient} Nuxeo Insight Cloud Client for REST communication
     * @return A {@link List} of AI_Model ids
     */
    protected List<String> getModelIds(CloudClient client) {
        try {
            JSONBlob models = client.getCloudAIModels();
            @SuppressWarnings("unchecked")
            Map<String, Serializable> resp = MAPPER.readValue(models.getStream(), Map.class);
            if (resp.containsKey(ENTRIES_KEY)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Serializable>> entries = (List<Map<String, Serializable>>) resp.get(ENTRIES_KEY);
                return entries.stream().map(entry -> (String) entry.get("uid")).collect(Collectors.toList());
            } else {
                log.debug("Could not find any entries in " + models.getString());
            }
        } catch (IOException e) {
            log.error(e);
        }

        return Collections.emptyList();
    }

    /**
     * Initiates export of data defined by Corpus Delta of each AI_Model
     *
     * @param client     {@link CloudClient} Nuxeo Insight Cloud Client for REST communication
     * @param uids       a {@link List} of AI_Model ids
     * @param repository Nuxeo repository to use for System Session
     */
    protected void initiateExport(CloudClient client, List<String> uids, String repository) {
        DatasetExportService exportService = Framework.getService(DatasetExportService.class);
        CoreSession session = CoreInstance.getCoreSessionSystem(repository);
        for (String uid : uids) {
            try {
                JSONBlob json = client.getCorpusDelta(uid);
                if (json == null) {
                    continue;
                }

                CorpusDelta delta = MAPPER.readValue(json.getStream(), CorpusDelta.class);
                if (delta.isEmpty()) {
                    continue;
                }
                String original = delta.getQuery();
                String modified = modifyQuery(original, delta.getEnd());
                if (checkMinimum(session, modified, delta.getMinSize())) {
                    String corporaId = delta.getCorporaId();
                    Map<String, Serializable> params = Collections.singletonMap(CORPORA_ID_PARAM, corporaId);

                    String jobId = exportService.export(session, modified, delta.getInputs(), delta.getOutputs(),
                            DEFAULT_SPLIT, params);
                    log.info("Initiating continues export for " + uid + " with job id " + jobId);
                } else {
                    log.info("Not enough documents to export; skipping");
                }
            } catch (IOException e) {
                log.error("Could not get corpus delta for model id " + uid, e);
            }
        }
    }

    /**
     * @param original NXQL Query used for exporting documents
     * @param calendar {@link Calendar} instance to exclude all models modified before the given date
     * @return A modified query
     */
    protected String modifyQuery(String original, Calendar calendar) {
        SQLQuery query = SQLQueryParser.parse(original);
        String isoTime = DateUtils.formatISODateTime(calendar);
        Predicate afterDatePred = new Predicate(new Reference(DC_MODIFIED), GT, new DateLiteral(isoTime, true));
        Predicate exclusive = new Predicate(NOT_VERSION_PRED, AND, afterDatePred);

        Predicate where;
        if (query.where != null && query.where.predicate != null) {
            where = new Predicate(query.where.predicate, AND, exclusive);
        } else {
            where = exclusive;
        }

        return new SQLQuery(query.select, query.from, new WhereClause(where), query.groupBy, query.having,
                query.orderBy, query.limit, query.offset).toString();
    }

    protected boolean checkMinimum(CoreSession session, String query, int min) {
        DocumentModelList list = session.query(query, min);
        return list.size() == min;
    }

    protected String getRepositoryName(EventBundle eb) {
        Optional<Event> event = StreamSupport.stream(eb.spliterator(), false)
                                             .filter(e -> Objects.nonNull(e.getContext()))
                                             .findFirst();

        String repository = DEFAULT_REPO;
        if (event.isPresent()) {
            repository = event.get().getContext().getRepositoryName();
        }
        return repository;
    }
}
