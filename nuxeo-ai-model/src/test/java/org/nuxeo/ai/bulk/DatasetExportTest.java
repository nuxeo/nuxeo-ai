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
package org.nuxeo.ai.bulk;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.util.stream.Collectors.groupingBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_CORPORA_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_EVALUATION_DATA;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_INPUTS;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_MODEL_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_MODEL_START_DATE;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_OUTPUTS;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_QUERY;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_SPLIT;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_STATS;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_TRAINING_DATA;
import static org.nuxeo.ai.bulk.TensorTest.countNumberOfExamples;
import static org.nuxeo.ai.model.export.DatasetExportServiceImpl.STATS_COUNT;
import static org.nuxeo.ai.model.export.DatasetExportServiceImpl.STATS_TOTAL;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.AI_CONVERSION_STRICT_MODE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.CATEGORY_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.IMAGE_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.NAME_PROP;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TEXT_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TYPE_PROP;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;
import static org.nuxeo.ecm.core.bulk.message.BulkStatus.State.ABORTED;
import static org.nuxeo.ecm.core.bulk.message.BulkStatus.State.COMPLETED;
import static org.nuxeo.ecm.core.bulk.message.BulkStatus.State.RUNNING;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_CARDINALITY;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_MISSING;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_TYPE_TERMS;

import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.model.analyzis.DatasetStatsService;
import org.nuxeo.ai.model.export.DatasetExportService;
import org.nuxeo.ai.model.export.DatasetStatsOperation;
import org.nuxeo.ai.sdk.objects.FieldStatistics;
import org.nuxeo.ai.sdk.objects.PropertyType;
import org.nuxeo.ai.sdk.objects.Statistic;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.audit.AuditFeature;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.elasticsearch.test.RepositoryElasticSearchFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RandomBug;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import org.nuxeo.runtime.transaction.TransactionHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.Sets;

@RunWith(FeaturesRunner.class)
@Features({ EnrichmentTestFeature.class, AutomationFeature.class, CoreBulkFeature.class,
        RepositoryElasticSearchFeature.class, AuditFeature.class })
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
@Deploy("org.nuxeo.ai.ai-core:OSGI-INF/recordwriter-test.xml")
@Deploy("org.nuxeo.ai.ai-model")
@Deploy("org.nuxeo.elasticsearch.core.test:elasticsearch-test-contrib.xml")
public class DatasetExportTest {

    public static final String TEST_MIME_TYPE = "image/png";

    private static final String TEST_DIR_PATH = "/bulkexporttest";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(
            options().extensions(new ResponseTemplateTransformer(true)).port(5089));

    @Inject
    public BulkService service;

    @Inject
    public CoreSession session;

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected AutomationService automationService;

    @Inject
    protected WorkManager workManager;

    @Inject
    protected ElasticSearchAdmin esa;

    @Inject
    protected DatasetExportService des;

    @Before
    public void setUp() throws Exception {
        setupTestData();
    }

    @After
    public void tearDown() {
        session.removeChildren(new PathRef("/"));
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/bulk-export-test.xml")
    @Ignore("Testing Stream concurrent cancel is not part of AI Addon")
    public void shouldCancelOnlyOneTask() throws InterruptedException {
        DocumentModel testRoot = session.getDocument(new PathRef(TEST_DIR_PATH));

        Set<PropertyType> input = Sets.newHashSet(new PropertyType("dc:title", TEXT_TYPE),
                new PropertyType("file:content", IMAGE_TYPE));
        Set<PropertyType> output = Sets.newHashSet(new PropertyType("dc:description", CATEGORY_TYPE));

        String nxql = String.format("SELECT * from Document where ecm:parentId='%s'", testRoot.getId());
        String toAbort = Framework.getService(DatasetExportService.class)
                                  .export(session, nxql, input, output, 60, null);

        String toComplete = Framework.getService(DatasetExportService.class)
                                     .export(session, nxql, input, output, 60, null);

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        service.await(Duration.ofSeconds(1));

        BulkStatus abortStatus = service.getStatus(toAbort);
        assertEquals(RUNNING, abortStatus.getState());

        assertEquals(ABORTED, service.abort(toAbort).getState());

        BulkStatus status = service.getStatus(toComplete);
        assertEquals(RUNNING, status.getState());

        service.await(Duration.ofSeconds(10));

        abortStatus = service.getStatus(toAbort);
        assertEquals(ABORTED, abortStatus.getState());

        status = service.getStatus(toComplete);
        assertEquals(COMPLETED, status.getState());

        String toRetryComplete = Framework.getService(DatasetExportService.class)
                                          .export(session, nxql, input, output, 60, null);

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        service.await(Duration.ofSeconds(1));

        BulkStatus retryStatus = service.getStatus(toRetryComplete);
        assertEquals(RUNNING, retryStatus.getState());

        service.await(Duration.ofSeconds(10));

        retryStatus = service.getStatus(toRetryComplete);
        assertEquals(COMPLETED, retryStatus.getState());
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    @RandomBug.Repeat(issue = "AICORE-412")
    @SuppressWarnings("unchecked")
    public void testBulkExport() throws Exception {
        Framework.getProperties().put(AI_CONVERSION_STRICT_MODE, "false");
        DocumentModel testRoot = session.getDocument(new PathRef(TEST_DIR_PATH));

        Set<PropertyType> input = Sets.newHashSet(new PropertyType("dc:title", TEXT_TYPE),
                new PropertyType("file:content", IMAGE_TYPE));
        Set<PropertyType> output = Sets.newHashSet(new PropertyType("dc:description", CATEGORY_TYPE));

        String nxql = "SELECT * from Document where ecm:parentId = " + NXQL.escapeString(testRoot.getId());
        String commandId = des.export(session, nxql, input, output, 60, null);

        assertTrue("Bulk action didn't finish", service.await(commandId, Duration.ofSeconds(30)));

        BulkStatus status = service.getStatus(commandId);
        assertNotNull(status);
        assertNotNull(status.getProcessingStartTime());
        assertNotNull(status.getProcessingEndTime());
        assertEquals(COMPLETED, status.getState());
        // 50 null records have been discarded, so we are left with 450 entries, split roughly 60 to 40 %
        assertEquals(450, status.getProcessed());
        DocumentModelList docs = des.getDatasetExports(session, "nonsense");
        assertThat(docs).isEmpty();

        docs = des.getDatasetExports(session, commandId);
        assertNotNull(docs);
        assertThat(docs).isNotEmpty().hasSize(5);

        Blob first = (Blob) docs.get(0).getPropertyValue(DATASET_EXPORT_STATS);
        Blob last = (Blob) docs.get(4).getPropertyValue(DATASET_EXPORT_STATS);

        List<Statistic> statsFirst = MAPPER.readValue(first.getFile(), List.class);
        List<Statistic> statsLast = MAPPER.readValue(last.getFile(), List.class);

        assertThat(statsFirst).isNotEqualTo(statsLast);

        int trainingCount = 0;
        int validationCount = 0;

        for (DocumentModel doc : docs) {
            assertThat((String) doc.getPropertyValue(DATASET_EXPORT_CORPORA_ID)).isNotNull().isNotEmpty();
            trainingCount += countNumberOfExamples((Blob) doc.getPropertyValue(DATASET_EXPORT_TRAINING_DATA), 3);
            validationCount += countNumberOfExamples((Blob) doc.getPropertyValue(DATASET_EXPORT_EVALUATION_DATA), 3);
        }

        String corporaId = (String) docs.get(0).getPropertyValue(DATASET_EXPORT_CORPORA_ID);
        assertThat(docs.stream()
                       .map(doc -> (String) doc.getPropertyValue(DATASET_EXPORT_CORPORA_ID))
                       .collect(Collectors.toList())).isNotEmpty().allMatch(val -> val.equals(corporaId));

        assertThat(trainingCount).isGreaterThan(validationCount);
        assertEquals("We should have discarded 50 bad blobs.", 400, trainingCount + validationCount);
        assertEquals("50 bad blobs.", 50, status.getErrorCount());
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void testBulkExportStrict() throws Exception {
        Framework.getProperties().put(AI_CONVERSION_STRICT_MODE, "true");
        DocumentModel testRoot = session.getDocument(new PathRef(TEST_DIR_PATH));

        Set<PropertyType> input = Sets.newHashSet(new PropertyType("dc:title", TEXT_TYPE),
                new PropertyType("file:content", IMAGE_TYPE));
        Set<PropertyType> output = Sets.newHashSet(new PropertyType("dc:description", CATEGORY_TYPE));

        String nxql = "SELECT * from Document where ecm:parentId = " + NXQL.escapeString(testRoot.getId());
        String commandId = des.export(session, nxql, input, output, 60, null);

        assertTrue("Bulk action didn't finish", service.await(commandId, Duration.ofSeconds(30)));

        BulkStatus status = service.getStatus(commandId);
        assertNotNull(status);
        assertNotNull(status.getProcessingStartTime());
        assertNotNull(status.getProcessingEndTime());
        assertEquals(COMPLETED, status.getState());
        // 50 null records have been discarded, so we are left with 450 entries, split roughly 60 to 40 %
        assertEquals(450, status.getProcessed());
        assertEquals(450, status.getErrorCount());
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    @RandomBug.Repeat(issue = "AICORE-412")
    public void shouldCallWithParameters() throws InterruptedException {
        Map<String, Serializable> params = new HashMap<>();

        String modelId = "e67ee0e8-1bef-4fb7-9966-1d14081221";
        params.put(DATASET_EXPORT_MODEL_ID, modelId);
        params.put(DATASET_EXPORT_MODEL_START_DATE, new Date());

        DocumentModel testRoot = session.getDocument(new PathRef(TEST_DIR_PATH));

        String nxql = "SELECT * from Document where ecm:parentId = " + NXQL.escapeString(testRoot.getId());

        Set<PropertyType> input = Sets.newHashSet(new PropertyType("dc:title", TEXT_TYPE),
                new PropertyType("file:content", IMAGE_TYPE));
        Set<PropertyType> output = Sets.newHashSet(new PropertyType("dc:created", CATEGORY_TYPE));

        String cmdId = Framework.getService(DatasetExportService.class)
                                .export(session, nxql, input, output, 60, params);

        assertNotNull(cmdId);
        assertTrue("Bulk action didn't finish", service.await(cmdId, Duration.ofSeconds(30)));

        BulkStatus status = service.getStatus(cmdId);
        assertNotNull(status);

        assertNotNull(status.getProcessingStartTime());
        assertNotNull(status.getProcessingEndTime());
        assertEquals(COMPLETED, status.getState());

        DocumentModel doc = getDatasetDoc(cmdId);

        assertEquals(nxql, doc.getPropertyValue(DATASET_EXPORT_QUERY));
        assertEquals(60L, doc.getPropertyValue(DATASET_EXPORT_SPLIT));
        assertEquals(modelId, doc.getPropertyValue(DATASET_EXPORT_MODEL_ID));
        Calendar startDate = (Calendar) doc.getPropertyValue(DATASET_EXPORT_MODEL_START_DATE);

        @SuppressWarnings("unchecked")
        List<Map<String, Serializable>> inputs = (List<Map<String, Serializable>>) doc.getPropertyValue(
                DATASET_EXPORT_INPUTS);
        assertEquals(2, inputs.size());
        assertTrue(inputs.stream()
                         .anyMatch(
                                 p -> "file:content".equals(p.get(NAME_PROP)) && IMAGE_TYPE.equals(p.get(TYPE_PROP))));

        @SuppressWarnings("unchecked")
        List<Map<String, Serializable>> outputs = (List<Map<String, Serializable>>) doc.getPropertyValue(
                DATASET_EXPORT_OUTPUTS);
        assertEquals(1, outputs.size());

        assertNotNull(startDate);
        assertThat(startDate.getTime()).isInSameDayAs(new Date());
    }

    protected DocumentModel getDatasetDoc(String returned) {
        DocumentModelList docs = des.getDatasetExports(session, returned);
        assertThat(docs).isNotEmpty();
        return docs.get(0);
    }

    /**
     * Wait for async worker completion then wait for indexing completion
     */
    public void waitForCompletion() throws Exception {
        workManager.awaitCompletion(20, TimeUnit.SECONDS);
        esa.prepareWaitForIndexing().get(20, TimeUnit.SECONDS);
        esa.refresh();
    }

    protected DocumentModel setupTestData() throws IOException {
        DocumentModel testRoot = session.createDocumentModel("/", "bulkexporttest", "Folder");
        testRoot = session.createDocument(testRoot);
        session.saveDocument(testRoot);

        Blob goodBlob = Blobs.createBlob(session.getClass().getResourceAsStream("/files/plane.jpg"), "image/jpeg");
        assertNotNull(goodBlob);

        DocumentModel test = session.getDocument(testRoot.getRef());

        for (int i = 0; i < 500; ++i) {
            DocumentModel doc = session.createDocumentModel(test.getPathAsString(), "doc" + i, "File");
            doc.setPropertyValue("dc:title", "doc_" + i % 2);
            doc.setPropertyValue("dc:description", "desc" + i % 4);
            if (i % 2 == 0) {
                doc.setPropertyValue("dc:language", "en" + i);
                doc.setPropertyValue("dc:subjects", new String[] { "sciences", "art/cinema" });
            }
            if (i % 10 != 0) {
                // 50 bad blobs, 400 good ones
                Blob blob = i % 5 == 0 ? Blobs.createBlob("My text" + i, TEST_MIME_TYPE) : goodBlob;
                doc.setPropertyValue("file:content", (Serializable) blob);
            }
            session.createDocument(doc);
        }

        txFeature.nextTransaction();
        return testRoot;
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void testBulkExportSubjects() throws Exception {
        DocumentModel testRoot = session.getDocument(new PathRef(TEST_DIR_PATH));
        waitForCompletion();

        Set<PropertyType> input = Sets.newHashSet(new PropertyType("dc:title", TEXT_TYPE));
        Set<PropertyType> output = Sets.newHashSet(new PropertyType("dc:subjects", CATEGORY_TYPE));

        String nxql = String.format("SELECT * from Document where ecm:parentId='%s'", testRoot.getId());
        String commandId = des.export(session, nxql, input, output, 80);

        txFeature.nextTransaction();

        assertTrue("Bulk action didn't finish", service.await(commandId, Duration.ofSeconds(30)));

        BulkStatus status = service.getStatus(commandId);
        assertNotNull(status);
        assertEquals(COMPLETED, status.getState());
        // All 500 records are processed, even the 50 records that have null subjects
        assertEquals(500, status.getProcessed());

        DocumentModelList docs = des.getDatasetExports(session, "nonsense");
        assertThat(docs).isEmpty();
        docs = des.getDatasetExports(session, commandId);
        assertThat(docs).isNotEmpty();

        int trainingCount = 0;
        int validationCount = 0;

        for (DocumentModel doc : docs) {
            trainingCount += countNumberOfExamples((Blob) doc.getPropertyValue(DATASET_EXPORT_TRAINING_DATA), -1);
            validationCount += countNumberOfExamples((Blob) doc.getPropertyValue(DATASET_EXPORT_EVALUATION_DATA), -1);
        }

        assertThat(trainingCount).isGreaterThan(validationCount);
        assertEquals(500, trainingCount + validationCount);
    }

    @Test
    public void testStats() throws Exception {
        DocumentModel testRoot = session.getDocument(new PathRef(TEST_DIR_PATH));
        waitForCompletion();

        Set<PropertyType> input = Sets.newHashSet(new PropertyType("dc:title", CATEGORY_TYPE),
                new PropertyType("file:content", IMAGE_TYPE));
        Set<PropertyType> output = Sets.newHashSet(new PropertyType("dc:description", CATEGORY_TYPE),
                new PropertyType("dc:language", CATEGORY_TYPE));

        String nxql = String.format("SELECT * from Document where ecm:parentId='%s'", testRoot.getId());
        Collection<Statistic> statistics = Framework.getService(DatasetStatsService.class)
                                                    .getStatistics(session, nxql, input, output);
        assertEquals("There should be 3 aggregates * 3 category fields + 2 agg missing content fields + 2 totals = 12",
                12, statistics.size());
        Map<String, List<Statistic>> byType = statistics.stream().collect(groupingBy(Statistic::getAggType));
        Map<String, List<Statistic>> byField = statistics.stream().collect(groupingBy(Statistic::getField));
        assertEquals("There should be 3 aggregates + 2 total = 5", 5, byType.size());
        Statistic total = byType.get(STATS_TOTAL).get(0);
        assertEquals(500, total.getNumericValue().intValue());
        total = byType.get(STATS_COUNT).get(0);
        assertEquals("There are 450 rows where all fields are not null.", 450, total.getNumericValue().intValue());
        assertEquals("There should be 4 fields + 2 total = 6", 6, byField.size());
        Statistic cardDesc = byType.get(AGG_CARDINALITY)
                                   .stream()
                                   .filter(a -> "dc:description".equals(a.getField()))
                                   .findFirst()
                                   .get();
        assertEquals(4, cardDesc.getNumericValue().intValue());
        Statistic termDesc = byType.get(AGG_TYPE_TERMS)
                                   .stream()
                                   .filter(a -> "dc:description".equals(a.getField()))
                                   .findFirst()
                                   .get();
        Statistic missingLang = byType.get(AGG_MISSING)
                                      .stream()
                                      .filter(a -> "dc:language".equals(a.getField()))
                                      .findFirst()
                                      .get();
        assertEquals(250, missingLang.getNumericValue().intValue());
        Statistic missingContent = byType.get(AGG_MISSING)
                                         .stream()
                                         .filter(a -> "file:content".equals(a.getField()))
                                         .findFirst()
                                         .get();
        assertEquals(50, missingContent.getNumericValue().intValue());
    }

    @Test
    public void shouldGetStatisticsOnAllProp() {
        String nxql = "SELECT * FROM Document WHERE ecm:primaryType = 'File'";
        DocumentModel doc0 = session.query(nxql, 1).get(0);
        List<String> collect = new ArrayList<>();
        for (String schema : doc0.getSchemas()) {
            for (String prop : doc0.getProperties(schema).keySet()) {
                if (prop.contains(":")) {
                    collect.add(prop);
                } else {
                    collect.add(schema + ":" + prop);
                }
            }
        }

        Set<PropertyType> inputs = collect.subList(0, collect.size() / 2)
                                          .stream()
                                          .map(prop -> new PropertyType(prop, null))
                                          .collect(Collectors.toSet());

        Set<PropertyType> outputs = collect.subList(collect.size() / 2, collect.size())
                                           .stream()
                                           .map(prop -> new PropertyType(prop, null))
                                           .collect(Collectors.toSet());

        assertThat(collect).isNotEmpty();
        assertThat(inputs.size() + outputs.size()).isEqualTo(collect.size());

        DatasetStatsService dss = Framework.getService(DatasetStatsService.class);
        Collection<Statistic> statistics = dss.getStatistics(session, nxql, inputs, outputs);

        Set<FieldStatistics> fieldStatistics = dss.transform(statistics);
        assertThat(fieldStatistics).isNotEmpty();

        Map<String, FieldStatistics> mapped = fieldStatistics.stream()
                                                             .collect(Collectors.toMap(FieldStatistics::getField,
                                                                     stat -> stat));
        assertThat(mapped.get("dc:subjects").getTerms()).isNotEmpty();
        assertThat(mapped.get("dc:subjects").getCardinality()).isEqualTo(2);

    }

    @Test
    public void testStatsOperation() throws Exception {
        DocumentModel testRoot = session.getDocument(new PathRef(TEST_DIR_PATH));
        waitForCompletion();

        // Now test the operation
        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();
        params.put("query", "SELECT * from document WHERE dc:title = 'i dont exist'");
        params.put("inputs", "dc:title,file:content");
        params.put("outputs", "dc:description,dc:subjects");

        Blob jsonBlob = (Blob) automationService.run(ctx, DatasetStatsOperation.ID, params);
        JsonNode jsonTree = MAPPER.readTree(jsonBlob.getString());
        assertEquals(0, jsonTree.size());

        params.put("query", "SELECT * from Document where ecm:parentId = " + NXQL.escapeString(testRoot.getId()));

        jsonBlob = (Blob) automationService.run(ctx, DatasetStatsOperation.ID, params);
        TypeReference<Set<FieldStatistics>> typeRef = new TypeReference<Set<FieldStatistics>>() {
        };
        Set<FieldStatistics> stats = MAPPER.readValue(jsonBlob.getString(), typeRef);
        assertEquals("There should be 4 field statistics", 4, stats.size());
        Optional<FieldStatistics> any = stats.stream().filter(s -> s.getField().equals("dc:description")).findAny();
        assertTrue(any.isPresent());
        assertThat(any.get().getTerms()).isNotEmpty();
    }
}
