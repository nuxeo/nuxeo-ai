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

import static java.util.Collections.shuffle;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.AIConstants.EXPORT_SPLIT_PARAM;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_CORPORA_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_MODEL_END_DATE;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_MODEL_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_MODEL_NAME;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_MODEL_START_DATE;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_TYPE;
import static org.nuxeo.ai.bulk.ExportHelper.getAvroCodec;
import static org.nuxeo.ai.bulk.ExportHelper.getKVS;
import static org.nuxeo.ai.model.export.CorpusDelta.CORPORA_ID_PARAM;
import static org.nuxeo.ai.model.export.DatasetExportServiceImpl.INPUT_PARAMETERS;
import static org.nuxeo.ai.model.export.DatasetExportServiceImpl.MODEL_PARAMETERS;
import static org.nuxeo.ai.model.export.DatasetExportServiceImpl.OUTPUT_PARAMETERS;
import static org.nuxeo.ai.model.export.DatasetExportServiceImpl.QUERY_PARAM;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;
import static org.nuxeo.ecm.core.schema.FacetNames.HIDDEN_IN_NAVIGATION;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.adapters.DatasetExport;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.cloud.CorporaParameters;
import org.nuxeo.ai.metadata.SuggestionMetadataWrapper;
import org.nuxeo.ai.model.export.DatasetExportService;
import org.nuxeo.ai.model.analyzis.DatasetStatsService;
import org.nuxeo.ai.model.analyzis.Statistic;
import org.nuxeo.ai.pipes.functions.PropertyUtils;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.pipes.types.ExportRecord;
import org.nuxeo.ai.pipes.types.PropertyType;
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
        CloudClient client = Framework.getService(CloudClient.class);
        if (client == null || !client.isAvailable()) {
            log.error("AI Client is not available; interrupting export " + command.getId());
            return;
        }

        DocumentModelList docs = loadDocuments(session, ids);
        shuffle(docs);

        KeyValueStore kvStore = getKVS();
        // Id to track the batch
        String batchId = UUID.randomUUID().toString();
        kvStore.put(batchId, (long) docs.size(), TIMEOUT_48_HOURS_IN_SEC);

        int split = (int) properties.getOrDefault(EXPORT_SPLIT_PARAM, DEFAULT_SPLIT);
        String original = (String) properties.get(QUERY_PARAM);

        @SuppressWarnings("unchecked")
        // re-create the properties for input and output
        Set<PropertyType> outputs = ((List<Map<String, Serializable>>) properties.get(
                OUTPUT_PARAMETERS)).stream()
                                   .map(p -> new PropertyType((String) p.get("name"), (String) p.get("type")))
                                   .collect(Collectors.toSet());
        @SuppressWarnings("unchecked")
        Set<PropertyType> inputs = ((List<Map<String, Serializable>>) properties.get(
                INPUT_PARAMETERS)).stream()
                                  .map(p -> new PropertyType((String) p.get("name"), (String) p.get("type")))
                                  .collect(Collectors.toSet());

        String nxql = buildQueryFrom(docs);
        Blob stats = getStatisticsBlob(session, nxql, inputs, outputs);

        @SuppressWarnings("unchecked")
        Map<String, Serializable> modelParams = (Map<String, Serializable>) properties.get(MODEL_PARAMETERS);
        if (modelParams == null) {
            modelParams = new HashMap<>();
        }

        createDataset(session, original, modelParams, inputs, outputs, stats, batchId, split);
        bindCorporaToModel(client, modelParams);

        // Split documents list into training and validation dataset
        int splitIndex = (int) Math.ceil((docs.size() - 1) * split / 100.f);

        Set<PropertyType> featuresList = new HashSet<>(inputs);
        featuresList.addAll(outputs);

        for (DocumentModel doc : docs.subList(0, splitIndex)) {
            ExportRecord record = createRecordFromDoc(batchId, featuresList, doc);
            training.add(record);
        }

        for (DocumentModel doc : docs.subList(splitIndex, docs.size())) {
            ExportRecord record = createRecordFromDoc(batchId, featuresList, doc);
            validation.add(record);
        }

        if (log.isDebugEnabled()) {
            log.debug("Training dataset size: {} - Validation dataset size: {} - Command {}", training.size(),
                    validation.size(), command.getId());
        }
    }

    protected void bindCorporaToModel(CloudClient client, Map<String, Serializable> modelParams) {
        if (modelParams.containsKey(CORPORA_ID_PARAM) && modelParams.containsKey(DATASET_EXPORT_MODEL_ID)) {
            String modelId = (String) modelParams.get(DATASET_EXPORT_MODEL_ID);
            String corporaId = (String) modelParams.get(CORPORA_ID_PARAM);
            boolean bound = client.bind((String) modelId,
                    corporaId);
            if (!bound) {
                log.error("Could not bind AI Model {} and AI Corpora {}", modelId, corporaId);
            }
        }
    }

    protected DatasetExport createDataset(CoreSession session, String original, Map<String, Serializable> modelParams,
            Set<PropertyType> inputs, Set<PropertyType> outputs, Blob stats, String batchId, int split) {
        return ExportHelper.runInTransaction(() -> {
            try (CloseableCoreSession sess = CoreInstance.openCoreSessionSystem(session.getRepositoryName(),
                    session.getPrincipal().getName())) {

                setCorporaId(sess, original, outputs, inputs, modelParams);
                DatasetExport dataset = createDataset(sess, original, inputs, outputs, split, stats, batchId);
                dataset.setJobId(command.getId());
                dataset.setBatchId(batchId);

                DocumentModel document = dataset.getDocument();

                propagateParameters(document, modelParams);

                sess.createDocument(document);
                return dataset;
            }
        });
    }

    protected void setCorporaId(CoreSession session, String original, Set<PropertyType> outputs,
            Set<PropertyType> inputs, Map<String, Serializable> modelParams) {
        if (StringUtils.isEmpty((String) modelParams.get(CORPORA_ID_PARAM))) {
            DatasetExportService des = Framework.getService(DatasetExportService.class);
            String corporaId = des.getCorporaForAction(session, command.getId());
            if (StringUtils.isEmpty(corporaId)) {
                CorporaParameters cp = new CorporaParameters();
                cp.setQuery(original);

                Set<PropertyType> fields = new HashSet<>(inputs);
                fields.addAll(outputs);
                cp.setFields(fields);
                corporaId = Framework.getService(CloudClient.class).initExport(null, cp);
            }

            modelParams.put(CORPORA_ID_PARAM, corporaId);
        }
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

    @Override
    public void processFailure(ComputationContext context, Throwable failure) {
        super.processFailure(context, failure);
        context.askForTermination();
    }

    /**
     * Create a corpus document for the data export.
     */
    public DatasetExport createDataset(CoreSession session, String query, Set<PropertyType> inputs,
            Set<PropertyType> outputs, int split, Blob statsBlob, String exportId) {
        if (log.isDebugEnabled()) {
            log.debug("Creating DatasetExport with Repository {} and User {}", session.getRepositoryName(),
                    session.getPrincipal().getActingUser());
        }
        DocumentModel doc = session.createDocumentModel(getRootFolder(session), "corpus_" + exportId,
                DATASET_EXPORT_TYPE);
        DatasetExport adapter = doc.getAdapter(DatasetExport.class);
        adapter.setQuery(query);
        adapter.setSplit(split);
        List<DatasetExport.IOParam> inputForAdp = inputs.stream().map(p -> new DatasetExport.IOParam() {
            {
                put("name", p.getName());
                put("type", p.getType());
            }
        }).collect(Collectors.toList());
        adapter.setInputs(inputForAdp);
        List<DatasetExport.IOParam> outputForAdp = outputs.stream().map(p -> new DatasetExport.IOParam() {
            {
                put("name", p.getName());
                put("type", p.getType());
            }
        }).collect(Collectors.toList());
        adapter.setOutputs(outputForAdp);
        adapter.setStatistics(statsBlob);

        return adapter;
    }

    private void propagateParameters(DocumentModel document, Map<String, Serializable> modelParams) {
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
                case CORPORA_ID_PARAM:
                    document.setPropertyValue(DATASET_EXPORT_CORPORA_ID, value);
                    break;
                default:
                    log.warn("Unknown property {} of type {}", key, value);
                }
            }
        }
    }

    @Nonnull
    protected Blob getStatisticsBlob(CoreSession session, String nxql, Set<PropertyType> input,
            Set<PropertyType> output) {
        Blob stats;
        try {
            Collection<Statistic> statistics = buildStatistics(session, nxql, input, output);
            stats = Blobs.createJSONBlobFromValue(statistics);
        } catch (IOException e) {
            throw new NuxeoException("Unable to process stats blob", e);
        }
        return stats;
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

    protected ExportRecord createRecordFromDoc(String id, Set<PropertyType> props, DocumentModel doc) {
        Map<String, String> nameTypePair = props.stream()
                                                .collect(
                                                        Collectors.toMap(PropertyType::getName, PropertyType::getType));

        if (doc.hasFacet(ENRICHMENT_FACET)) {
            SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(doc);
            for (String removeProperty : wrapper.getAutoFilled()) {
                nameTypePair.remove(removeProperty);
            }
            for (String removeProperty : wrapper.getAutoCorrected()) {
                nameTypePair.remove(removeProperty);
            }
        }

        BlobTextFromDocument subDoc = null;
        if (!nameTypePair.isEmpty()) {
            Set<PropertyType> properties = nameTypePair.entrySet()
                                                       .stream()
                                                       .map(p -> new PropertyType(p.getKey(), p.getValue()))
                                                       .collect(Collectors.toSet());
            subDoc = PropertyUtils.docSerialize(doc, properties);
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

    protected Collection<Statistic> buildStatistics(CoreSession session, String query, Set<PropertyType> input,
            Set<PropertyType> output) {
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
