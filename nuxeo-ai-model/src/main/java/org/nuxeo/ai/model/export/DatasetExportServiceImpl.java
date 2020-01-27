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
package org.nuxeo.ai.model.export;

import static java.util.Collections.emptyList;
import static org.nuxeo.ai.AIConstants.EXPORT_ACTION_NAME;
import static org.nuxeo.ai.AIConstants.EXPORT_SPLIT_PARAM;
import static org.nuxeo.ai.bulk.ExportHelper.propsToTypedList;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_BATCH_ID;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_JOB_ID;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.CATEGORY_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.IMAGE_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.NAME_PROP;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TEXT_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TYPE_PROP;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_CARDINALITY;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_MIN_DOC_COUNT_PROP;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_MISSING;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_SIZE_PROP;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_TYPE_TERMS;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.adapters.DatasetExport.IOParam;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.platform.query.core.AggregateDescriptor;
import org.nuxeo.elasticsearch.aggregate.AggregateEsBase;
import org.nuxeo.elasticsearch.aggregate.AggregateFactory;
import org.nuxeo.elasticsearch.api.ElasticSearchService;
import org.nuxeo.elasticsearch.api.EsResult;
import org.nuxeo.elasticsearch.query.NxQueryBuilder;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Exports data
 */
public class DatasetExportServiceImpl extends DefaultComponent implements DatasetExportService, DatasetStatsService {

    private static final Logger log = LogManager.getLogger(DatasetExportServiceImpl.class);

    protected static final Properties EMPTY_PROPS = new Properties();

    public static final String QUERY_PARAM = "query";

    public static final String INPUT_PROPERTIES = "inputProperties";

    public static final String OUTPUT_PROPERTIES = "outputProperties";

    public static final String INPUT_PARAMETERS = "inputParameters";

    public static final String OUTPUT_PARAMETERS = "outputParameters";

    public static final String MODEL_PARAMETERS = "modelParameters";

    public static final String STATISTICS_PARAM = "statistics";

    public static final String QUERY = "SELECT * FROM Document WHERE ecm:primaryType = "
            + NXQL.escapeString(DATASET_EXPORT_TYPE)
            + " AND ecm:isVersion = 0 AND ecm:isTrashed = 0 AND "
            + DATASET_EXPORT_JOB_ID + " = ";

    public static final String QUERY_FOR_BATCH = "SELECT * FROM Document WHERE ecm:primaryType = "
            + NXQL.escapeString(DATASET_EXPORT_TYPE)
            + " AND ecm:isVersion = 0 AND ecm:isTrashed = 0 AND "
            + DATASET_EXPORT_JOB_ID + " = %s AND " + DATASET_EXPORT_BATCH_ID + " = %s";


    public static final String STATS_TOTAL = "total";

    public static final String STATS_COUNT = "count";

    public static final String DEFAULT_NUM_TERMS = "200";

    public static final String DEFAULT_MIN_TERMS = "15";

    /**
     * Make an Aggregate using AggregateFactory.
     */
    protected static AggregateEsBase makeAggregate(String type, String field, Properties properties) {
        AggregateDescriptor descriptor = new AggregateDescriptor();
        descriptor.setId(aggKey(field, type));
        descriptor.setDocumentField(field);
        descriptor.setType(type);
        properties.forEach((key, value) -> descriptor.setProperty((String) key, (String) value));
        return AggregateFactory.create(descriptor, null);
    }

    protected static String aggKey(String propName, String s) {
        return s + "_" + propName;
    }

    @Override
    public String export(CoreSession session, String nxql,
                         Collection<String> inputProperties, Collection<String> outputProperties, int split) {
        return export(session, nxql, inputProperties, outputProperties, split, null);
    }

    @Override
    public String export(CoreSession session, String nxql,
                         Collection<String> inputProperties, Collection<String> outputProperties, int split,
                         Map<String, Serializable> modelParams) {

        validateParams(nxql, inputProperties, outputProperties);

        if (split < 1 || split > 100) {
            throw new IllegalArgumentException("Dataset split value is a percentage between 1 and 100");
        }

        List<IOParam> inputs = propsToTypedList(inputProperties);
        List<IOParam> outputs = propsToTypedList(outputProperties);

        List<IOParam> featuresWithType = new ArrayList<>(inputs);
        featuresWithType.addAll(outputs);

        String notNullNXQL = notNullNxql(nxql, featuresWithType);
        String username = session.getPrincipal().getName();
        BulkCommand bulkCommand = new BulkCommand.Builder(EXPORT_ACTION_NAME, notNullNXQL)
                .user(username)
                .repository(session.getRepositoryName())
                .param(QUERY_PARAM, nxql)
                .param(INPUT_PROPERTIES, (Serializable) inputProperties)
                .param(OUTPUT_PROPERTIES, (Serializable) outputProperties)
                .param(INPUT_PARAMETERS, (Serializable) inputs)
                .param(OUTPUT_PARAMETERS, (Serializable) outputs)
                .param(MODEL_PARAMETERS, (Serializable) modelParams)
                .param(EXPORT_SPLIT_PARAM, split)
                .build();

        if (log.isDebugEnabled()) {
            log.debug("Submitting command id: {}, for action {}", bulkCommand.getId(), bulkCommand.getAction());
        }

        return Framework.getService(BulkService.class)
                .submit(bulkCommand);
    }

    /**
     * Validate if the specified params are correct.
     */
    protected void validateParams(String nxql, Collection<String> inputProperties, Collection<String> outputProperties) {
        if (StringUtils.isBlank(nxql)
                || inputProperties == null || inputProperties.isEmpty()
                || outputProperties == null || outputProperties.isEmpty()) {
            throw new IllegalArgumentException("nxql and properties are required parameters");
        }
        if (!nxql.toUpperCase().contains("WHERE")) {
            throw new IllegalArgumentException("You cannot use an unbounded nxql query, please add a WHERE clause.");
        }
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
    public DocumentModel getBatchOf(String datasetJobId, CoreSession session, String id) {
        String query = String.format(QUERY_FOR_BATCH, NXQL.escapeString(datasetJobId), NXQL.escapeString(id));
        List<DocumentModel> docs = session.query(query, 1);
        if (docs.isEmpty()) {
            log.warn("Could not find any DatasetExport documents for id: {}", id);
            return null;
        } else {
            return docs.get(0);
        }
    }


    @Override
    public Collection<Statistic> getStatistics(CoreSession session, String nxql,
                                               Collection<String> inputProperties, Collection<String> outputProperties) {
        validateParams(nxql, inputProperties, outputProperties);
        List<String> featuresList = new ArrayList<>(inputProperties);
        featuresList.addAll(outputProperties);
        List<IOParam> featuresWithType = propsToTypedList(featuresList);

        List<Statistic> stats = new ArrayList<>();
        NxQueryBuilder qb = new NxQueryBuilder(session).nxql(nxql).limit(0);
        long total = getOverallStats(featuresWithType, stats, qb);
        if (total < 1) {
            return emptyList();
        }
        qb = new NxQueryBuilder(session).nxql(notNullNxql(nxql, featuresWithType)).limit(0);
        getValidStats(featuresWithType, stats, qb);
        return stats;
    }

    /**
     * Get the stats for the smaller dataset of valid values.
     */
    @SuppressWarnings("unchecked")
    protected void getValidStats(List<IOParam> featuresWithType, List<Statistic> stats, NxQueryBuilder qb) {
        for (Map<String, String> prop : featuresWithType) {
            String propName = prop.get(NAME_PROP);
            switch (prop.get(TYPE_PROP)) {
                case IMAGE_TYPE:
                    //           qb.addAggregate(makeAggregate(AGG_CARDINALITY, contentProperty(propName), EMPTY_PROPS));
                    break;
                default:
                    // Only 2 types at the moment, we would need numeric type in the future.
                    // TEXT_TYPE
                    Properties termProps = new Properties();
                    termProps.setProperty(AGG_SIZE_PROP, DEFAULT_NUM_TERMS);
                    termProps.setProperty(AGG_MIN_DOC_COUNT_PROP, DEFAULT_MIN_TERMS);
                    qb.addAggregate(makeAggregate(AGG_TYPE_TERMS, propName, termProps));
                    qb.addAggregate(makeAggregate(AGG_CARDINALITY, propName, EMPTY_PROPS));
            }
        }

        EsResult esResult = Framework.getService(ElasticSearchService.class).queryAndAggregate(qb);
        stats.addAll(esResult.getAggregates().stream()
                .map(Statistic::from)
                .collect(Collectors.toList()));
        stats.add(Statistic.of(STATS_COUNT, STATS_COUNT, STATS_COUNT, null,
                esResult.getElasticsearchResponse().getHits().getTotalHits()));
    }

    /**
     * Gets the overall stats for the dataset, before considering if the fields are valid.
     */
    @SuppressWarnings("unchecked")
    protected Long getOverallStats(List<IOParam> featuresWithType, List<Statistic> stats, NxQueryBuilder qb) {

        for (Map<String, String> prop : featuresWithType) {
            String propName = prop.get(NAME_PROP);
            switch (prop.get(TYPE_PROP)) {
                case CATEGORY_TYPE:
                case TEXT_TYPE:
                    qb.addAggregate(makeAggregate(AGG_MISSING, propName, EMPTY_PROPS));
                    break;
                case IMAGE_TYPE:
                    qb.addAggregate(makeAggregate(AGG_MISSING, contentProperty(propName), EMPTY_PROPS));
                    break;
                default:
                    // Only 3 types at the moment, we would need numeric type in the future.
            }
        }
        EsResult esResult = Framework.getService(ElasticSearchService.class).queryAndAggregate(qb);
        stats.addAll(esResult.getAggregates().stream().map(Statistic::from).collect(Collectors.toList()));
        Long total = esResult.getElasticsearchResponse().getHits().getTotalHits();
        stats.add(Statistic.of(STATS_TOTAL, STATS_TOTAL, STATS_TOTAL, null, total));
        return total;
    }

    protected String contentProperty(String propName) {
        return propName + "/length";
    }

    protected String notNullNxql(String nxql, List<IOParam> featuresWithType) {
        StringBuilder sb = new StringBuilder(nxql);
        for (Map<String, String> prop : featuresWithType) {
            String propName = prop.get(NAME_PROP);
            switch (prop.get(TYPE_PROP)) {
                case IMAGE_TYPE:
                    sb.append(" AND ").append(contentProperty(propName)).append(" IS NOT NULL");
                    break;
                case CATEGORY_TYPE:
                    // Don't add additional validation for the category type, it can be null.
                    break;
                default:
                    sb.append(" AND ").append(propName).append(" IS NOT NULL");
            }
        }
        return sb.toString();
    }


}
