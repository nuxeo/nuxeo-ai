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
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_JOBID;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.IMAGE_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.NAME_PROP;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TEXT_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TYPE_PROP;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.propsToTypedList;
import static org.nuxeo.ecm.core.schema.FacetNames.HIDDEN_IN_NAVIGATION;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_CARDINALITY;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_MISSING;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_SIZE_PROP;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_TYPE_TERMS;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.model.AiDocumentTypeConstants;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
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

    public static final PathRef PARENT_PATH = new PathRef("/" + CORPUS_TYPE);

    public static final String NUXEO_FOLDER = "Folder";

    public static final String STATS_TOTAL = "total";

    public static final String STATS_COUNT = "count";

    public static final String DEFAULT_NUM_BUCKETS = "20";

    protected static final Properties EMPTY_PROPS = new Properties();

    private static final Log log = LogFactory.getLog(DatasetExportServiceImpl.class);

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
        List<Map<String, String>> inputs = propsToTypedList(inputProperties);
        List<Map<String, String>> outputs = propsToTypedList(outputProperties);
        List<Map<String, String>> featuresWithType = new ArrayList<>(inputs);
        featuresWithType.addAll(outputs);
        DocumentModel corpus = createCorpus(session, nxql, inputs, outputs, split, statsBlob);

        List<String> featuresList = new ArrayList<>(inputProperties);
        featuresList.addAll(outputProperties);
        BulkCommand bulkCommand = new BulkCommand.Builder(EXPORT_ACTION_NAME, notNullNxql(nxql, featuresWithType))
                .repository(session.getRepositoryName())
                .user(session.getPrincipal().getName())
                .param(EXPORT_FEATURES_PARAM, String.join(",", featuresList))
                .param(EXPORT_SPLIT_PARAM, String.valueOf(split)).build();
        String bulkId = Framework.getService(BulkService.class).submit(bulkCommand);
        corpus.setPropertyValue(CORPUS_JOBID, bulkId);
        session.saveDocument(corpus);
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
    public DocumentModel createCorpus(CoreSession session, String query,
                                      List<Map<String, String>> inputs, List<Map<String, String>> outputs, int split,
                                      Blob statsBlob) {
        DocumentModel doc = session.createDocumentModel(getRootFolder(session), "corpor1", CORPUS_TYPE);
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_QUERY, query);
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_SPLIT, split);
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_INPUTS, (Serializable) inputs);
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_OUTPUTS, (Serializable) outputs);
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_STATS, (Serializable) statsBlob);
        return session.createDocument(doc);
    }

    @Override
    public DocumentModel getCorpusDocument(CoreSession session, String id) {
        List<DocumentModel> docs = session.query(String.format("SELECT * FROM %s WHERE %s = '%s'",
                                                               CORPUS_TYPE,
                                                               CORPUS_JOBID,
                                                               id));
        if (docs.size() == 1) {
            return docs.get(0);
        } else {
            log.warn(String.format("Corpus document error, there should only be 1 document for %s", id));
        }
        return null;
    }

    /**
     * Create the root folder if it doesn't exist
     */
    protected String getRootFolder(CoreSession session) {
        if (!session.exists(PARENT_PATH)) {
            DocumentModel doc = session.createDocumentModel("/", CORPUS_TYPE, NUXEO_FOLDER);
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
        List<Map<String, String>> featuresWithType = propsToTypedList(featuresList);

        List<Statistic> stats = new ArrayList<>();
        NxQueryBuilder qb = new NxQueryBuilder(session).nxql(nxql).limit(0);
        Long total = getOverallStats(featuresWithType, stats, qb);
        if (total < 1) {
            return emptyList();
        }
        qb = new NxQueryBuilder(session).nxql(notNullNxql(nxql, featuresWithType)).limit(0);
        getValidStats(featuresWithType, total, stats, qb);
        return stats;
    }

    /**
     * Get the stats for the smaller dataset of valid values.
     */
    @SuppressWarnings("unchecked")
    protected void getValidStats(List<Map<String, String>> featuresWithType,
                                 long total, List<Statistic> stats, NxQueryBuilder qb) {
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
                    termProps.setProperty(AGG_SIZE_PROP, DEFAULT_NUM_BUCKETS);
                    qb.addAggregate(makeAggregate(AGG_TYPE_TERMS, propName, termProps));
                    qb.addAggregate(makeAggregate(AGG_CARDINALITY, propName, EMPTY_PROPS));
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
    @SuppressWarnings("unchecked")
    protected Long getOverallStats(List<Map<String, String>> featuresWithType, List<Statistic> stats, NxQueryBuilder qb) {

        for (Map<String, String> prop : featuresWithType) {
            String propName = prop.get(NAME_PROP);
            switch (prop.get(TYPE_PROP)) {
                case TEXT_TYPE:
                    qb.addAggregate(makeAggregate(AGG_MISSING, propName, EMPTY_PROPS));
                    break;
                case IMAGE_TYPE:
                    qb.addAggregate(makeAggregate(AGG_MISSING, contentProperty(propName), EMPTY_PROPS));
                    break;
                default:
                    // Only 2 types at the moment, we would need numeric type in the future.
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

    protected String notNullNxql(String nxql, List<Map<String, String>> featuresWithType) {
        StringBuilder buffy = new StringBuilder(nxql);
        for (Map<String, String> prop : featuresWithType) {
            String propName = prop.get(NAME_PROP);
            switch (prop.get(TYPE_PROP)) {
                case IMAGE_TYPE:
                    buffy.append(" AND ").append(contentProperty(propName)).append(" IS NOT NULL");
                    break;
                default:
                    buffy.append(" AND ").append(propName).append(" IS NOT NULL");
            }
        }
        return buffy.toString();
    }
}
