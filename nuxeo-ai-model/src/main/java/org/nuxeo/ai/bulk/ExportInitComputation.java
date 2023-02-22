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
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
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
import static org.nuxeo.ai.pipes.functions.PropertyUtils.IMAGE_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.getPropertyValue;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;
import static org.nuxeo.ecm.core.query.sql.NXQL.escapeString;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.nuxeo.ai.adapters.DatasetExport;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.metadata.SuggestionMetadataWrapper;
import org.nuxeo.ai.model.analyzis.DatasetStatsService;
import org.nuxeo.ai.model.export.DatasetExportService;
import org.nuxeo.ai.pipes.functions.PropertyUtils;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.pipes.types.ExportRecord;
import org.nuxeo.ai.sdk.objects.CorporaParameters;
import org.nuxeo.ai.sdk.objects.PropertyType;
import org.nuxeo.ai.sdk.objects.Statistic;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CloseableCoreSession;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.lib.stream.codec.Codec;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.runtime.api.Framework;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Splits document into batches and randomly splits it into 2 groups for training and validation.
 */
public class ExportInitComputation extends AbstractBulkComputation {

    public static final String UUID_QUERY_INIT = "SELECT * FROM Document WHERE ecm:uuid IN ";

    public static final long TIMEOUT_48_HOURS_IN_SEC = 48 * 60 * 60;

    public static final String COMMA_DELIMITER = ",";

    private static final String TIMEOUT_KV_STORE = "nuxeo.ai.timeout.kv.store";

    public static final int DEFAULT_SPLIT = 75;

    public static final PathRef PARENT_PATH = new PathRef("/" + DATASET_EXPORT_TYPE);

    public static final String NUXEO_FOLDER = "Folder";

    private static final Logger log = LogManager.getLogger(ExportInitComputation.class);

    protected List<ExportRecord> suitable = new LinkedList<>();

    protected List<ExportRecord> failed = new LinkedList<>();

    protected int split = DEFAULT_SPLIT;

    private Boolean strictMode = null;

    public ExportInitComputation(String name) {
        super(name, 1);

    }

    @Override
    protected void compute(CoreSession session, List<String> ids, Map<String, Serializable> properties) {
        CloudClient client = Framework.getService(CloudClient.class);
        if (client == null || !client.isAvailable(session)) {
            log.error("AI Client is not available; interrupting export " + command.getId());
            return;
        }

        split = (int) properties.getOrDefault(EXPORT_SPLIT_PARAM, DEFAULT_SPLIT);
        // re-create the properties for input and output
        Set<PropertyType> outputs = toPropertyTypeSet(properties, OUTPUT_PARAMETERS);
        Set<PropertyType> inputs = toPropertyTypeSet(properties, INPUT_PARAMETERS);

        DocumentModelList docs = loadDocuments(session, ids);

        @SuppressWarnings("unchecked")
        Map<String, Serializable> modelParams = (Map<String, Serializable>) properties.get(MODEL_PARAMETERS);
        if (modelParams == null) {
            modelParams = new HashMap<>();
        }

        // ID to track the batch
        String batchId = UUID.randomUUID().toString();
        docs.stream().map(doc -> createRecordFromDoc(batchId, inputs, outputs, doc)).forEach(rec -> {
            if (rec.isFailed()) {
                failed.add(rec);
            } else {
                suitable.add(rec);
            }
        });

        getKVS().put(batchId, (long) docs.size(),
                Long.parseLong(Framework.getProperty(TIMEOUT_KV_STORE, String.valueOf(TIMEOUT_48_HOURS_IN_SEC))));
        if (suitable.isEmpty()) {
            log.warn("No suitable documents found in batch {}; failed size {}", batchId, failed);
        }

        String nxql = buildQueryFrom(docs);
        String original = (String) properties.get(QUERY_PARAM);
        Blob stats = getStatisticsBlob(session, nxql, inputs, outputs);
        createDataset(session, original, modelParams, inputs, outputs, stats, batchId, split);
        bindCorporaToModel(session, client, modelParams);
    }

    @SuppressWarnings("unchecked")
    private static Set<PropertyType> toPropertyTypeSet(Map<String, Serializable> properties, String outputParameters) {
        return ((List<Map<String, Serializable>>) properties.get(outputParameters)).stream()
                                                                                   .map(p -> new PropertyType(
                                                                                           (String) p.get("name"),
                                                                                           (String) p.get("type")))
                                                                                   .collect(Collectors.toSet());
    }

    @Override
    public void endBucket(ComputationContext context, BulkStatus ignored) {
        shuffle(suitable);
        int trainingSize = (int) (suitable.size() * (split / 100.0));
        suitable.subList(0, trainingSize).forEach(r -> r.setTraining(true));

        Codec<ExportRecord> codec = getAvroCodec(ExportRecord.class);
        for (ExportRecord record : suitable) {
            context.produceRecord(OUTPUT_1, record.getId(), codec.encode(record));
        }

        // Distribute the failed records evenly
        for (int i = 0; i < failed.size(); i++) {
            ExportRecord rec = failed.get(i);
            if (i % 2 == 0) {
                rec.setTraining(true);
            }

            context.produceRecord(OUTPUT_1, rec.getId(), codec.encode(rec));
        }

        log.warn("Initialized Batch of {} suitable records; Failed {}; Command {}", suitable.size(), failed.size(),
                command.getId());
        failed.clear();
        suitable.clear();
        context.askForCheckpoint();
    }

    @Override
    public void processFailure(ComputationContext context, Throwable failure) {
        super.processFailure(context, failure);
        suitable.clear();
        failed.clear();
        throw new NuxeoException(failure);
    }

    protected void bindCorporaToModel(CoreSession session, CloudClient client, Map<String, Serializable> modelParams) {
        if (modelParams.containsKey(CORPORA_ID_PARAM) && modelParams.containsKey(DATASET_EXPORT_MODEL_ID)) {
            String modelId = (String) modelParams.get(DATASET_EXPORT_MODEL_ID);
            String corporaId = (String) modelParams.get(CORPORA_ID_PARAM);
            boolean bound = client.bind(session, modelId, corporaId);
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
                corporaId = Framework.getService(CloudClient.class).initExport(session, null, cp);
            }

            modelParams.put(CORPORA_ID_PARAM, corporaId);
        }
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

        List<Map<String, String>> inputForAdp = inputs.stream()
                                                      .map(ExportInitComputation::ioParams)
                                                      .collect(Collectors.toList());
        adapter.setInputs(inputForAdp);
        List<Map<String, String>> outputForAdp = outputs.stream()
                                                        .map(ExportInitComputation::ioParams)
                                                        .collect(Collectors.toList());
        adapter.setOutputs(outputForAdp);
        adapter.setStatistics(statsBlob);

        return adapter;
    }

    @NotNull
    private static Map<String, String> ioParams(PropertyType p) {
        Map<String, String> map = new HashMap<>();
        map.put("name", p.getName());
        map.put("type", p.getType());
        return map;
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

    protected String buildQueryFrom(DocumentModelList docs) {
        return UUID_QUERY_INIT + " (" + docs.stream()
                                            .map(doc -> escapeString(doc.getId()))
                                            .collect(joining(COMMA_DELIMITER)) + ")";
    }

    protected ExportRecord createRecordFromDoc(String id, Set<PropertyType> inputs, Set<PropertyType> outputs,
            DocumentModel doc) {
        Map<String, String> nameTypePair = Stream.concat(inputs.stream(), outputs.stream())
                                                 .filter(prop -> Objects.nonNull(prop.getType()))
                                                 .collect(toMap(PropertyType::getName, PropertyType::getType, (a, b) -> a));

        if (doc.hasFacet(ENRICHMENT_FACET)) {
            SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(doc);
            wrapper.getAutoFilled().stream().map(val -> val.get("xpath")).forEach(nameTypePair::remove);
            wrapper.getAutoCorrected().stream().map(val -> val.get("xpath")).forEach(nameTypePair::remove);
        }

        BlobTextFromDocument subDoc = null;
        if (!nameTypePair.isEmpty()) {
            Set<String> outNames = outputs.stream().map(PropertyType::getName).collect(Collectors.toSet());
            Set<PropertyType> properties = nameTypePair.entrySet()
                                                       .stream()
                                                       .map(p -> new PropertyType(p.getKey(), p.getValue()))
                                                       .collect(Collectors.toSet());
            boolean outputPresent = false;
            subDoc = PropertyUtils.docSerialize(doc, properties, getConversionMode());
            for (PropertyType pType : properties) {
                Serializable propVal = getPropertyValue(doc, pType.getName());
                if ((propVal instanceof ManagedBlob) && IMAGE_TYPE.equals(pType.getType())) {
                    if (subDoc.getBlobs().get(pType.getName()) == null) {
                        subDoc = null;
                        log.warn("An empty blob encountered in Document {} for property {}", doc.getId(),
                                pType.getName());
                        break;
                    }
                }

                if (outNames.contains(pType.getName())) {
                    String property = subDoc.getProperty(pType.getName());
                    if (StringUtils.isNotEmpty(property)) {
                        outputPresent = true;
                    } else {
                        log.warn("Document {} has no value for {}", subDoc.getId(), pType.getName());
                    }
                }
            }

            if (subDoc != null && !outputPresent) {
                log.warn("Document {} has no output properties values: {}", subDoc.getId(), outNames);
                subDoc = null;
            }
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

    protected boolean getConversionMode() {
        if (strictMode == null) {
            strictMode = PropertyUtils.getConversionMode();
        }

        return strictMode;
    }
}
