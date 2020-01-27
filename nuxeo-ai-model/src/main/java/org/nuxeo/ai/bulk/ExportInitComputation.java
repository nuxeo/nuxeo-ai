/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.bulk;

import static java.util.Arrays.asList;
import static java.util.Collections.shuffle;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.AIConstants.EXPORT_SPLIT_PARAM;
import static org.nuxeo.ai.bulk.ExportHelper.getAvroCodec;
import static org.nuxeo.ai.bulk.ExportHelper.getKVS;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_MODEL_END_DATE;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_MODEL_ID;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_MODEL_NAME;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_MODEL_START_DATE;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_TYPE;
import static org.nuxeo.ai.model.export.DatasetExportServiceImpl.INPUT_PARAMETERS;
import static org.nuxeo.ai.model.export.DatasetExportServiceImpl.INPUT_PROPERTIES;
import static org.nuxeo.ai.model.export.DatasetExportServiceImpl.MODEL_PARAMETERS;
import static org.nuxeo.ai.model.export.DatasetExportServiceImpl.OUTPUT_PARAMETERS;
import static org.nuxeo.ai.model.export.DatasetExportServiceImpl.OUTPUT_PROPERTIES;
import static org.nuxeo.ai.model.export.DatasetExportServiceImpl.QUERY_PARAM;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;
import static org.nuxeo.ecm.core.schema.FacetNames.HIDDEN_IN_NAVIGATION;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.nuxeo.ai.adapters.DatasetExport;
import org.nuxeo.ai.adapters.DatasetExport.IOParam;
import org.nuxeo.ai.metadata.SuggestionMetadataWrapper;
import org.nuxeo.ai.model.export.DatasetStatsService;
import org.nuxeo.ai.model.export.Statistic;
import org.nuxeo.ai.pipes.functions.PropertyUtils;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.pipes.types.ExportRecord;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CloseableCoreSession;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.lib.stream.codec.Codec;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueStore;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Splits document into batches and randomly splits it into 2 groups for training and validation.
 */
public class ExportInitComputation extends AbstractBulkComputation {

    private static final Logger log = LogManager.getLogger(ExportInitComputation.class);

    public static final String UUID_QUERY_INIT = "SELECT * FROM Document WHERE ecm:uuid IN ";

    public static final long TIMEOUT_48_HOURS_IN_SEC = 48 * 60 * 60;

    public static final int DEFAULT_SPLIT = 75;

    public static final PathRef PARENT_PATH = new PathRef("/" + DATASET_EXPORT_TYPE);

    public static final String NUXEO_FOLDER = "Folder";

    protected List<ExportRecord> training = new LinkedList<>();

    protected List<ExportRecord> validation = new LinkedList<>();

    public ExportInitComputation(String name) {
        super(name, 2);

    }

    @Override
    protected void compute(CoreSession session, List<String> ids, Map<String, Serializable> properties) {
        @SuppressWarnings("unchecked")
        List<String> input = (List<String>) properties.get(INPUT_PROPERTIES);
        @SuppressWarnings("unchecked")
        List<String> output = (List<String>) properties.get(OUTPUT_PROPERTIES);

        // Id to track the batch
        String exportId = UUID.randomUUID().toString();

        DocumentModelList docs = loadDocuments(session, ids);
        shuffle(docs);

        KeyValueStore kvStore = getKVS();
        kvStore.put(exportId, (long) docs.size(), TIMEOUT_48_HOURS_IN_SEC);

        List<String> featuresList = new ArrayList<>(input);
        featuresList.addAll(output);
        String[] props = featuresList.toArray(new String[0]);

        int split = (int) properties.getOrDefault(EXPORT_SPLIT_PARAM, DEFAULT_SPLIT);
        // Split documents list into training and validation dataset
        int splitIndex = (int) Math.ceil((docs.size() - 1) * split / 100.f);

        for (DocumentModel doc : docs.subList(0, splitIndex)) {
            ExportRecord record = createRecordFromDoc(exportId, props, doc);
            training.add(record);
        }

        for (DocumentModel doc : docs.subList(splitIndex, docs.size())) {
            ExportRecord record = createRecordFromDoc(exportId, props, doc);
            validation.add(record);
        }

        String original = (String) properties.get(QUERY_PARAM);
        String nxql = buildQueryFrom(docs);
        createDataset(session, original, nxql, properties, input, output, exportId, split);

        if (log.isDebugEnabled()) {
            log.debug("Training dataset size: {} - Validation dataset size: {} - Command {}", training.size(),
                    validation.size(), command.getId());
        }
    }

    private void createDataset(CoreSession session, String original, String nxql, Map<String, Serializable> properties,
                               List<String> input, List<String> output, String exportId, int split) {
        @SuppressWarnings("unchecked")
        List<IOParam> outputs = (List<IOParam>) properties.get(OUTPUT_PARAMETERS);
        @SuppressWarnings("unchecked")
        List<IOParam> inputs = (List<IOParam>) properties.get(INPUT_PARAMETERS);

        Blob stats = getStatisticsBlob(session, nxql, input, output);

        ExportHelper.runInTransaction(() -> {
            try (CloseableCoreSession sess =
                         CoreInstance.openCoreSessionSystem(session.getRepositoryName(),
                                 session.getPrincipal().getName())) {
                DatasetExport dataset = createDataset(sess, original, inputs, outputs, split, stats, exportId);
                dataset.setJobId(command.getId());
                dataset.setBatchId(exportId);

                DocumentModel document = dataset.getDocument();

                @SuppressWarnings("unchecked")
                Map<String, Serializable> modelParams = (Map<String, Serializable>) properties.get(MODEL_PARAMETERS);
                if (modelParams != null) {
                    for (String key : modelParams.keySet()) {
                        Serializable value = modelParams.get(key);
                        switch (key) {
                            case DATASET_EXPORT_MODEL_END_DATE:
                            case DATASET_EXPORT_MODEL_START_DATE:
                                document.setPropertyValue(key, new Date((long) value));
                                break;
                            case DATASET_EXPORT_MODEL_ID:
                            case DATASET_EXPORT_MODEL_NAME:
                                document.setPropertyValue(key, value);
                                break;
                            default:
                                log.warn("Unknown property {} of type {}", key, value);
                        }

                    }
                }

                sess.createDocument(document);
            }

            return null;
        });
    }

    @NotNull
    private Blob getStatisticsBlob(CoreSession session, String nxql, List<String> input, List<String> output) {
        Blob stats;
        try {
            Collection<Statistic> statistics = buildStatistics(session, nxql, input, output);
            stats = Blobs.createJSONBlobFromValue(statistics);
        } catch (IOException e) {
            throw new NuxeoException("Unable to process stats blob", e);
        }
        return stats;
    }

    @Override
    public void endBucket(ComputationContext context, BulkStatus ignored) {
        Codec<ExportRecord> codec = getAvroCodec(ExportRecord.class);

        training.forEach(record -> context.produceRecord(OUTPUT_1, command.getId(), codec.encode(record)));
        training.clear();

        validation.forEach(record -> context.produceRecord(OUTPUT_2, command.getId(), codec.encode(record)));
        validation.clear();

        context.askForCheckpoint();
    }

    /**
     * Create a corpus document for the data export.
     */
    public DatasetExport createDataset(CoreSession session, String query,
                                       List<IOParam> inputs, List<IOParam> outputs,
                                       int split, Blob statsBlob, String exportId) {
        if (log.isDebugEnabled()) {
            log.debug("Creating DatasetExport with Repository {} and User {}", session.getRepositoryName(),
                    session.getPrincipal().getActingUser());
        }
        DocumentModel doc = session.createDocumentModel(getRootFolder(session),
                "corpus_" + exportId, DATASET_EXPORT_TYPE);
        DatasetExport adapter = doc.getAdapter(DatasetExport.class);
        adapter.setQuery(query);
        adapter.setSplit(split);
        adapter.setInputs(inputs);
        adapter.setOutputs(outputs);
        adapter.setStatistics(statsBlob);

        return adapter;
    }

    protected String buildQueryFrom(DocumentModelList documents) {
        StringBuilder sb = new StringBuilder(UUID_QUERY_INIT);
        sb.append(" (");

        for (DocumentModel doc : documents) {
            sb.append(NXQL.escapeString(doc.getId()));
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(")");
        return sb.toString();
    }

    protected ExportRecord createRecordFromDoc(String id, String[] props, DocumentModel doc) {
        List<String> targetProperties = new ArrayList<>(asList(props));

        if (doc.hasFacet(ENRICHMENT_FACET)) {
            SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(doc);
            targetProperties.removeAll(wrapper.getAutoFilled());
            targetProperties.removeAll(wrapper.getAutoCorrected());
        }

        BlobTextFromDocument subDoc = null;
        if (!targetProperties.isEmpty()) {
            subDoc = PropertyUtils.docSerialize(doc, new HashSet<>(targetProperties));
        }

        if (subDoc != null) {
            try {
                return ExportRecord.of(id, command.getId(), MAPPER.writeValueAsBytes(subDoc));
            } catch (JsonProcessingException e) {
                throw new NuxeoException(e);
            }
        } else {
            log.error("Couldn't create record from document {}", doc.getId());
            return ExportRecord.fail(id, command.getId());
        }
    }

    protected Collection<Statistic> buildStatistics(CoreSession session, String query, List<String> input, List<String> output) {
        DatasetStatsService dss = Framework.getService(DatasetStatsService.class);
        return dss.getStatistics(session, query, input, output);
    }

    /**
     * Create the root folder if it doesn't exist
     */
    protected String getRootFolder(CoreSession session) {
        if (!session.exists(PARENT_PATH)) {
            DocumentModel doc = session.createDocumentModel("/", DATASET_EXPORT_TYPE, NUXEO_FOLDER);
            doc.addFacet(HIDDEN_IN_NAVIGATION);
            session.createDocument(doc);
        }

        return PARENT_PATH.toString();
    }
}
