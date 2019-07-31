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
import static org.nuxeo.ai.AIConstants.EXPORT_FEATURES_PARAM;
import static org.nuxeo.ai.AIConstants.EXPORT_SPLIT_PARAM;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_JOB_ID;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.CATEGORY_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.IMAGE_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.NAME_PROP;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TEXT_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TYPE_PROP;
import static org.nuxeo.ecm.core.schema.FacetNames.HIDDEN_IN_NAVIGATION;
import static org.nuxeo.ecm.core.schema.TypeConstants.isContentType;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_CARDINALITY;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_MIN_DOC_COUNT_PROP;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_MISSING;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_SIZE_PROP;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_TYPE_TERMS;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.adapters.DatasetExport;
import org.nuxeo.ai.adapters.DatasetExport.IOParam;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
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

    public static final String QUERY = "SELECT * FROM " + DATASET_EXPORT_TYPE
            + " WHERE ecm:isVersion = 0 AND ecm:isTrashed = 0 AND "
            + DATASET_EXPORT_JOB_ID + " = ";

    public static final PathRef PARENT_PATH = new PathRef("/" + DATASET_EXPORT_TYPE);

    public static final String NUXEO_FOLDER = "Folder";

    public static final String STATS_TOTAL = "total";

    public static final String STATS_COUNT = "count";

    public static final String DEFAULT_NUM_TERMS = "200";

    public static final String DEFAULT_MIN_TERMS = "15";

    protected static final Properties EMPTY_PROPS = new Properties();

    private static final Logger log = LogManager.getLogger(DatasetExportServiceImpl.class);

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

        Blob statsBlob;
        try {
            statsBlob = Blobs.createJSONBlobFromValue(getStatistics(session, nxql, inputProperties, outputProperties));
        } catch (IOException e) {
            throw new NuxeoException("Unable to process stats blob", e);
        }
        List<IOParam> inputs = propsToTypedList(inputProperties);
        List<IOParam> outputs = propsToTypedList(outputProperties);

        List<IOParam> featuresWithType = new ArrayList<>(inputs);
        featuresWithType.addAll(outputs);
        DatasetExport dataset = createDataset(session, nxql, inputs, outputs, split, statsBlob);

        List<String> featuresList = new ArrayList<>(inputProperties);
        featuresList.addAll(outputProperties);

        String notNullNXQL = notNullNxql(nxql, featuresWithType);
        String username = session.getPrincipal().getName();
        BulkCommand bulkCommand = new BulkCommand.Builder(EXPORT_ACTION_NAME, notNullNXQL)
                .user(username)
                .repository(session.getRepositoryName())
                .param(EXPORT_FEATURES_PARAM, String.join(",", featuresList))
                .param(EXPORT_SPLIT_PARAM, String.valueOf(split)).build();

        String bulkId = Framework.getService(BulkService.class).submit(bulkCommand);
        dataset.setJobId(bulkId);

        DocumentModel document = dataset.getDocument();
        if (modelParams != null) {
            for (String key : modelParams.keySet()) {
                document.setPropertyValue(key, modelParams.get(key));
            }
        }

        session.createDocument(document);

        return bulkId;
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

    /**
     * Create a corpus document for the data export.
     */
    public DatasetExport createDataset(CoreSession session, String query,
                                       List<IOParam> inputs, List<IOParam> outputs, int split,
                                       Blob statsBlob) {
        DocumentModel doc = session.createDocumentModel(getRootFolder(session), "corpor1", DATASET_EXPORT_TYPE);
        DatasetExport adapter = doc.getAdapter(DatasetExport.class);
        adapter.setQuery(query);
        adapter.setSplit(split);
        adapter.setInputs(inputs);
        adapter.setOutputs(outputs);
        adapter.setStatistics(statsBlob);

        return adapter;
    }

    @Override
    public DocumentModel getDatasetExportDocument(CoreSession session, String id) {
        List<DocumentModel> docs = session.query(QUERY + NXQL.escapeString(id), 1);
        if (docs.isEmpty()) {
            log.warn("Could not find any DatasetExport documents for id: {}", id);
        } else {
            return docs.get(0);
        }
        return null;
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

    @Override
    public Collection<Statistic> getStatistics(CoreSession session, String nxql,
                                               Collection<String> inputProperties, Collection<String> outputProperties) {
        validateParams(nxql, inputProperties, outputProperties);
        List<String> featuresList = new ArrayList<>(inputProperties);
        featuresList.addAll(outputProperties);
        List<IOParam> featuresWithType = propsToTypedList(featuresList);

        List<Statistic> stats = new ArrayList<>();
        NxQueryBuilder qb = new NxQueryBuilder(session).nxql(nxql).limit(0);
        Long total = getOverallStats(featuresWithType, stats, qb);
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
        StringBuilder buffy = new StringBuilder(nxql);
        for (Map<String, String> prop : featuresWithType) {
            String propName = prop.get(NAME_PROP);
            switch (prop.get(TYPE_PROP)) {
                case IMAGE_TYPE:
                    buffy.append(" AND ").append(contentProperty(propName)).append(" IS NOT NULL");
                    break;
                case CATEGORY_TYPE:
                    // Don't add additional validation for the category type, it can be null.
                    break;
                default:
                    buffy.append(" AND ").append(propName).append(" IS NOT NULL");
            }
        }
        return buffy.toString();
    }

    /**
     * For the given property, find out if it exists and determine if its text or content
     */
    protected IOParam getPropertyWithType(String prop) {
        Field field = Framework.getService(SchemaManager.class).getField(prop);
        IOParam feature = new IOParam();

        feature.put(NAME_PROP, prop);
        if (field == null) {
            if (NXQL.ECM_FULLTEXT.equals(prop)) {
                log.debug("Skipping {} because its not possible to get stats on it.", NXQL.ECM_FULLTEXT);
                return null;
            } else {
                log.warn(prop + " does not exist as a type, defaulting to txt type.");
                feature.put(TYPE_PROP, TEXT_TYPE);
            }
            return feature;
        }
        String type = isContentType(field.getType()) ? IMAGE_TYPE : TEXT_TYPE;
        if (field.getType().isListType()) {
            type = CATEGORY_TYPE;
        }
        feature.put(TYPE_PROP, type);
        return feature;
    }


    /**
     * For a given Collection of property names, return a list of features with the property name and type.
     */
    protected List<IOParam> propsToTypedList(Collection<String> properties) {
        return properties.stream()
                .map(this::getPropertyWithType)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
