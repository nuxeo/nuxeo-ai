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
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_BATCH_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_CORPORA_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_JOB_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.CATEGORY_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.IMAGE_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TEXT_TYPE;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_CARDINALITY;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_MIN_DOC_COUNT_PROP;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_MISSING;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_SIZE_PROP;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_TYPE_TERMS;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.search.aggregations.Aggregation;
import org.nuxeo.ai.bulk.ExportHelper;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.model.analyzis.DatasetStatsService;
import org.nuxeo.ai.model.analyzis.Statistic;
import org.nuxeo.ai.pipes.types.PropertyType;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.platform.query.api.Bucket;
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
            + NXQL.escapeString(DATASET_EXPORT_TYPE) + " AND ecm:isVersion = 0 AND ecm:isTrashed = 0 AND "
            + DATASET_EXPORT_JOB_ID + " = ";

    public static final String QUERY_FOR_BATCH = "SELECT * FROM Document WHERE ecm:primaryType = "
            + NXQL.escapeString(DATASET_EXPORT_TYPE) + " AND ecm:isVersion = 0 AND ecm:isTrashed = 0 AND "
            + DATASET_EXPORT_JOB_ID + " = %s AND " + DATASET_EXPORT_BATCH_ID + " = %s";

    public static final String STATS_TOTAL = "total";

    public static final String STATS_COUNT = "count";

    public static final String DEFAULT_NUM_TERMS = "200";

    public static final String DEFAULT_MIN_TERMS = "15";

    @Override
    public String export(CoreSession session, String nxql, Set<PropertyType> inputProperties,
            Set<PropertyType> outputProperties, int split) {
        return export(session, nxql, inputProperties, outputProperties, split, null);
    }

    @Override
    public String export(CoreSession session, String nxql, Set<PropertyType> inputs, Set<PropertyType> outputs,
            int split, Map<String, Serializable> modelParams) {

        List<String> inputNames = inputs.stream().map(p -> p.getName()).collect(Collectors.toList());
        List<String> outputNames = outputs.stream().map(p -> p.getName()).collect(Collectors.toList());
        validateParams(nxql, inputNames, outputNames);

        if (split < 1 || split > 100) {
            throw new IllegalArgumentException("Dataset split value is a percentage between 1 and 100");
        }

        List<PropertyType> featuresWithType = new ArrayList<>(inputs);
        featuresWithType.addAll(outputs);

        String notNullNXQL = notNullNxql(nxql, featuresWithType);
        String username = session.getPrincipal().getName();
        List<Map<String, String>> inputAsParameter = inputs.stream().map(p -> new HashMap<String, String>() {
            {
                put("name", p.getName());
                put("type", p.getType());
            }
        }).collect(Collectors.toList());
        List<Map<String, String>> outputAsParameter = outputs.stream().map(p -> new HashMap<String, String>() {
            {
                put("name", p.getName());
                put("type", p.getType());
            }
        }).collect(Collectors.toList());
        BulkCommand bulkCommand = new BulkCommand.Builder(EXPORT_ACTION_NAME,
                notNullNXQL).user(username)
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
        if (client == null || !client.isAvailable()) {
            throw new NuxeoException("AI Client is not available; interrupting export " + bulkCommand.getId());
        }
        return Framework.getService(BulkService.class).submit(bulkCommand);
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
    public Collection<Statistic> getStatistics(CoreSession session, String nxql, Set<PropertyType> inputProperties,
            Set<PropertyType> outputProperties) {
        Set<String> inputPropNames = inputProperties.stream().map(PropertyType::getName).collect(Collectors.toSet());
        Set<String> outputPropNames = outputProperties.stream().map(PropertyType::getName).collect(Collectors.toSet());
        validateParams(nxql, inputPropNames, outputPropNames);

        List<PropertyType> featuresList = inputProperties.stream()
                                                         .map(ExportHelper::addTypeIfNull)
                                                         .collect(Collectors.toList());
        featuresList.addAll(outputProperties.stream().map(ExportHelper::addTypeIfNull).collect(Collectors.toList()));

        List<Statistic> stats = new ArrayList<>();
        NxQueryBuilder qb = new NxQueryBuilder(session).nxql(nxql).limit(0);
        long total = getOverallStats(featuresList, stats, qb);
        if (total < 1) {
            return emptyList();
        }
        qb = new NxQueryBuilder(session).nxql(notNullNxql(nxql, featuresList)).limit(0);
        getValidStats(featuresList, stats, qb);
        return stats;
    }

    /**
     * Make an Aggregate using AggregateFactory.
     */
    protected static AggregateEsBase<? extends Aggregation, ? extends Bucket> makeAggregate(String type, String field,
            Properties properties) {
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

    /**
     * Validate if the specified params are correct.
     */
    protected void validateParams(String nxql, Collection<String> inputProperties,
            Collection<String> outputProperties) {
        if (StringUtils.isBlank(nxql) || inputProperties == null || inputProperties.isEmpty()
                || outputProperties == null || outputProperties.isEmpty()) {
            throw new IllegalArgumentException("nxql and properties are required parameters");
        }
        if (!nxql.toUpperCase().contains("WHERE")) {
            throw new IllegalArgumentException("You cannot use an unbounded nxql query, please add a WHERE clause.");
        }
    }

    /**
     * Get the stats for the smaller dataset of valid values.
     */
    @SuppressWarnings("unchecked")
    protected void getValidStats(List<PropertyType> featuresWithType, List<Statistic> stats, NxQueryBuilder qb) {
        for (PropertyType prop : featuresWithType) {
            String propName = prop.getName();
            if (IMAGE_TYPE.equals(prop.getType())) {
                // qb.addAggregate(makeAggregate(AGG_CARDINALITY, contentProperty(propName), EMPTY_PROPS));
            } else if (CATEGORY_TYPE.equals(prop.getType()) || TEXT_TYPE.equals(prop.getType())) {
                Properties termProps = new Properties();
                termProps.setProperty(AGG_SIZE_PROP, DEFAULT_NUM_TERMS);
                termProps.setProperty(AGG_MIN_DOC_COUNT_PROP, DEFAULT_MIN_TERMS);
                qb.addAggregate(makeAggregate(AGG_TYPE_TERMS, propName, termProps));
                qb.addAggregate(makeAggregate(AGG_CARDINALITY, propName, EMPTY_PROPS));
            } else {
                log.warn("Trying to get statistics for an unknown type: {}", prop.getType());
            }
        }

        EsResult esResult = Framework.getService(ElasticSearchService.class).queryAndAggregate(qb);
        stats.addAll(esResult.getAggregates().stream().map(Statistic::from).collect(Collectors.toList()));
        stats.add(Statistic.of(STATS_COUNT, STATS_COUNT, STATS_COUNT, null,
                esResult.getElasticsearchResponse().getHits().getTotalHits()));
    }

    /**
     * Gets the overall stats for the dataset, before considering if the fields are valid.
     */
    protected Long getOverallStats(List<PropertyType> featuresWithType, List<Statistic> stats, NxQueryBuilder qb) {
        for (PropertyType prop : featuresWithType) {
            String propName = prop.getName();
            if (prop.getType() != null) {
                switch (prop.getType()) {
                case CATEGORY_TYPE:
                case TEXT_TYPE:
                    // TODO assuming that text is a property ! could be a blob
                    qb.addAggregate(makeAggregate(AGG_MISSING, propName, EMPTY_PROPS));
                    break;
                case IMAGE_TYPE:
                    qb.addAggregate(makeAggregate(AGG_MISSING, contentProperty(propName), EMPTY_PROPS));
                    break;
                default:
                    // Only 3 types at the moment, we would need numeric type in the future.
                }
            } else {
                // TODO assuming without type it is text or category !
                qb.addAggregate(makeAggregate(AGG_MISSING, propName, EMPTY_PROPS));
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

    protected String notNullNxql(String nxql, List<PropertyType> featuresWithType) {
        StringBuilder sb = new StringBuilder(nxql);
        // SchemaManager serv = Framework.getService(SchemaManager.class);
        // Field field = serv.getField(propName);
        for (PropertyType prop : featuresWithType) {
            String propName = prop.getName();
            if (IMAGE_TYPE.equals(prop.getType())) {
                sb.append(" AND ").append(contentProperty(propName)).append(" IS NOT NULL");
            } else if (CATEGORY_TYPE.equals(prop.getType())) {
                // nothing to do here
            } else {
                sb.append(" AND ").append(propName).append(" IS NOT NULL");
            }
        }
        return sb.toString();
    }

}
