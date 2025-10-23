package org.nuxeo.ai.model.export;

import static java.util.Collections.emptyList;
import static org.nuxeo.ai.AIConstants.EXPORT_ACTION_NAME;
import static org.nuxeo.ai.AIConstants.EXPORT_SPLIT_PARAM;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_BATCH_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_CORPORA_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_JOB_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_TYPE;
import static org.nuxeo.ai.bulk.ExportInitComputation.DEFAULT_SPLIT;
import static org.nuxeo.ai.model.export.CorpusDelta.CORPORA_ID_PARAM;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.CATEGORY_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.IMAGE_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TEXT_TYPE;
import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.BULK_KV_STORE_NAME;
import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.STATUS_PREFIX;
import static org.nuxeo.ecm.core.query.sql.model.Operator.AND;
import static org.nuxeo.ecm.core.query.sql.model.Operator.EQ;
import static org.nuxeo.ecm.core.query.sql.model.Operator.GT;
import static org.nuxeo.ecm.core.storage.BaseDocument.DC_MODIFIED;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.bulk.ExportHelper;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.model.analyzis.DatasetStatsService;
import org.nuxeo.ai.sdk.objects.DataType;
import org.nuxeo.ai.sdk.objects.PropertyType;
import org.nuxeo.ai.sdk.objects.Statistic;
import org.nuxeo.ai.utils.DateUtils;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.ecm.core.bulk.*;
import org.nuxeo.ecm.core.bulk.message.*;
import org.nuxeo.ecm.core.query.sql.*;
import org.nuxeo.ecm.core.query.sql.model.*;
import org.nuxeo.ecm.core.schema.*;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.TypeConstants;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.query.nxql.CoreQueryDocumentPageProvider;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.*;
import org.nuxeo.runtime.model.DefaultComponent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

/**
 * DatasetExportServiceImpl — rewritten to avoid Elasticsearch/internal APIs.
 * - Uses CoreSession + NXQL and in-memory aggregation for stats.
 * - Compatible with Nuxeo 2025.3 without protected builders.
 *
 * Notes:
 * - For very large datasets, stats() is potentially expensive (it fetches documents).
 * - If you have a search backend with aggregation support, we can later swap to that.
 */
public class DatasetExportServiceImpl extends DefaultComponent implements DatasetExportService, DatasetStatsService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger log = LogManager.getLogger(DatasetExportServiceImpl.class);

    private static final String IS_VERSION_PROP = "ecm:isVersion";
    private static final long TWO_DAYS_IN_SEC = TimeUnit.DAYS.toSeconds(2);
    private static final String AI_RUNNING_EXPORTS_KVS = "aiRunningExports";

    private static final Predicate NOT_VERSION_PRED = new Predicate(new Reference(IS_VERSION_PROP), EQ,
            new IntegerLiteral(0));

    protected static final String BASE_QUERY =
            "SELECT * FROM Document WHERE ecm:primaryType = " + NXQL.escapeString(DATASET_EXPORT_TYPE)
                    + " AND ecm:isVersion = 0 AND ecm:isTrashed = 0 AND ";

    protected static final Set<String> VALID_DOC_TYPES = Sets.newHashSet(
            DataType.IMAGE.shorten(), DataType.TEXT.shorten(), DataType.CATEGORY.shorten(), null);

    protected static final Properties TERM_PROPS;
    protected static final Properties EMPTY_PROPS = new Properties();

    public static final String DEFAULT_NUM_TERMS = "200";
    public static final String QUERY_PARAM = "query";
    public static final String INPUT_PARAMETERS = "inputParameters";
    public static final String OUTPUT_PARAMETERS = "outputParameters";
    public static final String MODEL_PARAMETERS = "modelParameters";
    public static final String STATS_TOTAL = "total";
    public static final String STATS_COUNT = "count";

    public static final String QUERY = BASE_QUERY + DATASET_EXPORT_JOB_ID + " = ";
    public static final String QUERY_FOR_BATCH =
            BASE_QUERY + DATASET_EXPORT_JOB_ID + " = %s AND " + DATASET_EXPORT_BATCH_ID + " = %s";

    static {
        TERM_PROPS = new Properties();
        TERM_PROPS.setProperty("size", DEFAULT_NUM_TERMS);
    }

    // ---------------------------
    // EXPORT: submit/export-by-ids, tracking, statuses
    // ---------------------------

    @Override
    public String export(CoreSession session, String nxql, Set<PropertyType> inputProperties,
            Set<PropertyType> outputProperties, int split) {
        return export(session, nxql, inputProperties, outputProperties, split, null);
    }

    @Override
    public String export(CoreSession session, String nxql, Set<PropertyType> inputs,
            Set<PropertyType> outputs, int split, Map<String, Serializable> modelParams) {
        validate(nxql, inputs, outputs);
        if (split < 1 || split > 100) {
            throw new IllegalArgumentException("Dataset split value is a percentage between 1 and 100");
        }

        List<PropertyType> featuresWithType = new ArrayList<>(inputs);
        featuresWithType.addAll(outputs);

        String notNullNXQL = notNullNxql(nxql, featuresWithType);
        String username = session.getPrincipal().getName();
        List<Map<String, String>> inputAsParameter = inputs.stream().map(this::toMap).collect(Collectors.toList());
        List<Map<String, String>> outputAsParameter = outputs.stream().map(this::toMap).collect(Collectors.toList());

        BulkCommand bulkCommand = new BulkCommand.Builder(EXPORT_ACTION_NAME, notNullNXQL, username)
                .repository(session.getRepositoryName())
                .param(QUERY_PARAM, nxql)
                .param(INPUT_PARAMETERS, (Serializable) inputAsParameter)
                .param(OUTPUT_PARAMETERS, (Serializable) outputAsParameter)
                .param(MODEL_PARAMETERS, (Serializable) modelParams)
                .param(EXPORT_SPLIT_PARAM, split)
                .build();

        if (log.isDebugEnabled()) {
            log.debug("Submitting command id: {}, for action {}", bulkCommand.getId(), bulkCommand.getAction());
        }

        CloudClient client = Framework.getService(CloudClient.class);
        if (client == null || !client.isAvailable(session)) {
            throw new NuxeoException("AI Client is not available; interrupting export " + bulkCommand.getId());
        }
        return Framework.getService(BulkService.class).submit(bulkCommand);
    }

    @Override
    public void export(CoreSession session, Set<String> uids) {
        CloudClient client = Framework.getService(CloudClient.class);
        KeyValueStore kvs = Framework.getService(KeyValueService.class).getKeyValueStore(AI_RUNNING_EXPORTS_KVS);
        Set<String> statuses = getStatuses().stream()
                                            .filter(this::isRunning)
                                            .map(BulkStatus::getId)
                                            .collect(Collectors.toSet());
        for (String uid : uids) {
            log.info("Preparing export for model " + uid);
            String exportId = kvs.getString(uid);
            if (statuses.contains(exportId)) {
                log.info("Export {} for model {} is already running; skipping", exportId, uid);
                continue;
            }

            CorpusDelta delta = getCorpusDelta(session, client, uid);
            if (delta == null || delta.isEmpty()) {
                log.info("Delta is empty for model " + uid);
                continue;
            }

            String original = delta.getQuery();
            String modified = modifyQuery(original, delta.getEnd());
            if (checkMinimum(session, modified, delta.getMinSize())) {
                Map<String, Serializable> params = Collections.singletonMap(CORPORA_ID_PARAM, delta.getCorporaId());
                String jobId = export(session, modified, delta.getInputs(), delta.getOutputs(), DEFAULT_SPLIT, params);
                log.info("Initiating continuous export for Model " + uid + " with job id " + jobId);
                kvs.put(uid, jobId, TWO_DAYS_IN_SEC);
            } else {
                log.info("Not enough documents to export; skipping");
            }
        }
    }

    protected void validate(String nxql, Set<PropertyType> inputs, Set<PropertyType> outputs) {
        if (StringUtils.isBlank(nxql) || inputs.isEmpty() || outputs.isEmpty()) {
            throw new IllegalArgumentException("nxql and properties are required parameters");
        }

        if (!nxql.toUpperCase().contains("WHERE")) {
            throw new IllegalArgumentException("You cannot use an unbounded nxql query, please add a WHERE clause.");
        }

        boolean valid = Stream.concat(inputs.stream(), outputs.stream())
                              .allMatch(p -> VALID_DOC_TYPES.contains(p.getType()));
        if (!valid) {
            throw new IllegalArgumentException("Input and Output types must be an image, text, category or null");
        }

        boolean disjoint = Collections.disjoint(inputs, outputs);
        if (!disjoint) {
            throw new IllegalArgumentException("Input and Output properties must be disjoint");
        }
    }

    protected Map<String, String> toMap(PropertyType p) {
        Map<String, String> map = new HashMap<>();
        map.put("name", p.getName());
        map.put("type", p.getType());
        return map;
    }

    // ---------------------------
    // STATUS helpers
    // ---------------------------

    protected boolean isRunning(BulkStatus status) {
        return status.getState() == BulkStatus.State.RUNNING || status.getState() == BulkStatus.State.SCROLLING_RUNNING
                || status.getState() == BulkStatus.State.SCHEDULED;
    }

    @Override
    public List<BulkStatus> getStatuses() {
        KeyValueStore kvs = Framework.getService(KeyValueService.class).getKeyValueStore(BULK_KV_STORE_NAME);
        KeyValueStoreProvider kv = (KeyValueStoreProvider) kvs;
        return kv.keyStream(STATUS_PREFIX)
                 .map(kv::get)
                 .map(BulkCodecs.getStatusCodec()::decode)
                 .filter(status -> EXPORT_ACTION_NAME.equals(status.getAction()))
                 .collect(Collectors.toList());
    }

    // ---------------------------
    // Corpus delta helpers
    // ---------------------------

    @Nullable
    protected CorpusDelta getCorpusDelta(CoreSession session, CloudClient client, String uid) {
        JSONBlob json;
        try {
            json = client.getCorpusDelta(session, uid);
            if (json == null) {
                log.debug("No corpus delta for Model" + uid);
                return null;
            }

            return MAPPER.readValue(json.getStream(), CorpusDelta.class);
        } catch (IOException e) {
            log.error("Could not get corpus delta for model id " + uid, e);
            return null;
        }
    }

    // ---------------------------
    // Query modification and utils
    // ---------------------------

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

    @Override
    public DocumentModelList getDatasetExports(CoreSession session, @Nonnull String id) {
        DocumentModelList docs = session.query(QUERY + NXQL.escapeString(id));
        if (docs.isEmpty()) {
            log.warn("Could not find any DatasetExport documents for id: {}", id);
        } else {
            return docs;
        }

        return new DocumentModelListImpl();
    }

    @Override
    public DocumentModel latestDatasetExportForModel(CoreSession session, @Nonnull String modelId) {
        PageProviderService pps = Framework.getService(PageProviderService.class);
        Map<String, Serializable> props = new HashMap<>();
        props.put(CoreQueryDocumentPageProvider.CORE_SESSION_PROPERTY, (Serializable) session);
        final String DATASET_EXPORT_DESC_PP = "dataset_export_desc";
        @SuppressWarnings("unchecked")
        PageProvider<DocumentModel> pp = (PageProvider<DocumentModel>) pps.getPageProvider(DATASET_EXPORT_DESC_PP, null,
                1L, 0L, props, modelId);
        List<DocumentModel> currentPage = pp.getCurrentPage();
        if (currentPage.isEmpty()) {
            return null;
        }
        // Get the latest created Dataset Export
        return currentPage.get(0);
    }

    @Override
    public String getCorporaForAction(CoreSession session, String exportJobId) {
        DocumentModelList corpora = session.query(QUERY + NXQL.escapeString(exportJobId), 1);
        if (corpora.isEmpty()) {
            return null;
        }
        return (String) corpora.get(0).getPropertyValue(DATASET_EXPORT_CORPORA_ID);
    }

    @Override
    public DocumentModel getCorpusOfBatch(CoreSession session, String exportJobId, String batchId) {
        String query = String.format(QUERY_FOR_BATCH, NXQL.escapeString(exportJobId), NXQL.escapeString(batchId));
        List<DocumentModel> docs = session.query(query, 1);
        if (docs.isEmpty()) {
            log.warn("Could not find any DatasetExport documents for batchId: {}", batchId);
            return null;
        } else {
            return docs.get(0);
        }
    }

    @Override
    public List<String> getRunningExports() {
        return List.of();
    }

    @Override
    public void markExportAsRunning(String id) {

    }

    // ---------------------------
    // STATISTICS implemented using NXQL + in-memory aggregation
    // ---------------------------

    @Override
    public Collection<Statistic> getStatistics(CoreSession session, String nxql, Set<PropertyType> inputProperties,
            Set<PropertyType> outputProperties) {
        validate(nxql, inputProperties, outputProperties);
        List<PropertyType> featuresList = inputProperties.stream()
                                                         .map(ExportHelper::addTypeIfNull)
                                                         .collect(Collectors.toList());
        featuresList.addAll(outputProperties.stream().map(ExportHelper::addTypeIfNull).collect(Collectors.toList()));

        List<Statistic> stats = new ArrayList<>();
        // 1) get total count for the NXQL as-is
        DocumentModelList allDocs = session.query(nxql);
        long total = allDocs.size();
        if (total < 1) {
            return emptyList();
        }
        stats.add(Statistic.of(STATS_TOTAL, STATS_TOTAL, STATS_TOTAL, STATS_TOTAL, total));

        // 2) For each property compute missing, cardinality, top terms and counts
        // We'll scan documents and compute per-property metrics in-memory
        Map<String, PropertyAccumulator> accumulators = new HashMap<>();
        SchemaManager schemaManager = Framework.getService(SchemaManager.class);
        for (PropertyType prop : featuresList) {
            String name = prop.getName();
            accumulators.put(name, new PropertyAccumulator(name, prop, schemaManager));
        }

        // walk documents and update accumulators
        for (DocumentModel dm : allDocs) {
            for (PropertyAccumulator acc : accumulators.values()) {
                acc.consume(dm);
            }
        }

        // produce statistics from accumulators
        for (PropertyAccumulator acc : accumulators.values()) {
            // missing stat
            Statistic missing = Statistic.of(acc.missingId(), acc.fieldName, acc.inputType(), "missing", acc.missingCount());
            stats.add(missing);

            // count stat (non-null)
            Statistic count = Statistic.of(acc.countId(), acc.fieldName, acc.inputType(), "count", acc.count());
            stats.add(count);

            // cardinality stat
            Statistic card = Statistic.of(acc.cardId(), acc.fieldName, acc.inputType(), "cardinality", acc.cardinality());
            stats.add(card);

            // top terms as buckets
            List<org.nuxeo.ai.sdk.objects.Bucket> buckets = acc.topTermsAsBuckets();
            if (!buckets.isEmpty()) {
                Statistic termsStat = Statistic.of(acc.termsId(), acc.fieldName, acc.inputType(), "terms", (Number) buckets.size());
                termsStat.setValue(buckets);
                stats.add(termsStat);
            }
        }

        // 3) add count of valid dataset values (not-null according to property types)
        String notNullNXQL = notNullNxql(nxql, featuresList);
        DocumentModelList validDocs = session.query(notNullNXQL);
        long validCount = validDocs.size();
        stats.add(Statistic.of(STATS_COUNT, STATS_COUNT, STATS_COUNT, STATS_COUNT, validCount));

        return stats;
    }

    /** Very small helper to accumulate property metrics while scanning documents */
    protected static class PropertyAccumulator {
        final String fieldName;
        final PropertyType propType;
        final String inputType; // derived type name for Statistic
        long missing = 0;
        long count = 0;
        final Map<String, Long> termCounts = new HashMap<>();

        PropertyAccumulator(String fieldName, PropertyType propType, SchemaManager sm) {
            this.fieldName = fieldName;
            this.propType = propType;
            Field f = sm.getField(fieldName);
            this.inputType = DatasetStatsService.getInputType(f);
        }

        void consume(DocumentModel dm) {
            try {
                // For blobs (images/text as blob) check .length property if exists
                if (IMAGE_TYPE.equals(propType.getType())) {
                    Serializable len = (Serializable) dm.getPropertyValue(fieldName + "/length");
                    Long l = (len instanceof Number) ? ((Number) len).longValue() : null;
                    if (l == null || l <= 0) {
                        missing++;
                    } else {
                        count++;
                    }
                } else {
                    Serializable v = (Serializable) dm.getPropertyValue(fieldName);
                    if (v == null) {
                        missing++;
                    } else {
                        // treat arrays / lists and single values
                        if (v instanceof String) {
                            String s = (String) v;
                            if (s.trim().isEmpty()) {
                                missing++;
                            } else {
                                count++;
                                termCounts.merge(s, 1L, Long::sum);
                            }
                        } else if (v instanceof String[]) {
                            String[] arr = (String[]) v;
                            boolean any = false;
                            for (String s : arr) {
                                if (s != null && !s.trim().isEmpty()) {
                                    termCounts.merge(s, 1L, Long::sum);
                                    any = true;
                                }
                            }
                            if (any) count++; else missing++;
                        } else if (v instanceof Collection) {
                            Collection<?> c = (Collection<?>) v;
                            boolean any = false;
                            for (Object o : c) {
                                if (o != null) {
                                    String key = String.valueOf(o);
                                    termCounts.merge(key, 1L, Long::sum);
                                    any = true;
                                }
                            }
                            if (any) count++; else missing++;
                        } else {
                            // other types: count as present and index value string
                            count++;
                            termCounts.merge(String.valueOf(v), 1L, Long::sum);
                        }
                    }
                }
            } catch (Exception e) {
                // property missing or not accessible; treat as missing
                missing++;
            }
        }

        long missingCount() {
            return missing;
        }

        long count() {
            return count;
        }

        long cardinality() {
            return termCounts.size();
        }

        String missingId() {
            return "missing_" + fieldName;
        }

        String countId() {
            return "count_" + fieldName;
        }

        String cardId() {
            return "cardinality_" + fieldName;
        }

        String termsId() {
            return "terms_" + fieldName;
        }

        String inputType() {
            return inputType == null ? "unknown" : inputType;
        }

        List<org.nuxeo.ai.sdk.objects.Bucket> topTermsAsBuckets() {
            if (termCounts.isEmpty()) return Collections.emptyList();
            int size =  Integer.parseInt(Optional.ofNullable(System.getProperty("nuxeo.ai.terms.size")).orElse("200"));
            return termCounts.entrySet().stream()
                             .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                             .limit(size)
                             .map(e -> new org.nuxeo.ai.sdk.objects.Bucket(e.getKey(), e.getValue()))
                             .collect(Collectors.toList());
        }
    }

    // ---------------------------
    // Helpers: notNull Nxql and blob detection
    // ---------------------------

    protected String notNullNxql(String nxql, List<PropertyType> featuresWithType) {
        StringBuilder sb = new StringBuilder(nxql);
        SchemaManager serv = Framework.getService(SchemaManager.class);
        for (PropertyType prop : featuresWithType) {
            String propName = prop.getName();
            Field field = serv.getField(propName);
            if (IMAGE_TYPE.equals(prop.getType()) || isTextBlob(prop, field)) {
                sb.append(" AND ").append(propName).append("/length IS NOT NULL");
            } else if (CATEGORY_TYPE.equals(prop.getType()) || TEXT_TYPE.equals(prop.getType())) {
                // nothing to add for normal fields
            } else {
                log.warn("Unknown Property type " + prop.getType());
            }
        }
        return sb.toString();
    }

    protected boolean isTextBlob(PropertyType prop, Field field) {
        return TEXT_TYPE.equals(prop.getType()) && TypeConstants.isContentType(field.getType());
    }
}
