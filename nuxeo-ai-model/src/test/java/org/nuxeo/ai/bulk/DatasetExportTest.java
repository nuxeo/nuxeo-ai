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

import static java.util.stream.Collectors.groupingBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.bulk.CollectingDataSetDoneListener.makeKey;
import static org.nuxeo.ai.bulk.DataSetBulkAction.TRAINING_COMPUTATION;
import static org.nuxeo.ai.bulk.DataSetBulkAction.VALIDATION_COMPUTATION;
import static org.nuxeo.ai.bulk.DataSetExportStatusComputation.DATASET_EXPORT_DONE_EVENT;
import static org.nuxeo.ai.bulk.TensorTest.countNumberOfExamples;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.getBlobFromProvider;
import static org.nuxeo.ai.model.export.DatasetExportServiceImpl.STATS_COUNT;
import static org.nuxeo.ai.model.export.DatasetExportServiceImpl.STATS_TOTAL;
import static org.nuxeo.ecm.core.bulk.message.BulkStatus.State.COMPLETED;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_CARDINALITY;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_MISSING;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.AGG_TYPE_TERMS;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.model.export.DatasetExportService;
import org.nuxeo.ai.model.export.DatasetStatsService;
import org.nuxeo.ai.model.export.Statistic;
import org.nuxeo.ai.pipes.services.PipelineService;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.elasticsearch.test.RepositoryElasticSearchFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import javax.inject.Inject;
import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, AutomationFeature.class, PlatformFeature.class, CoreBulkFeature.class, RepositoryElasticSearchFeature.class})
@Deploy("org.nuxeo.ai.ai-core:OSGI-INF/recordwriter-test.xml")
@Deploy("org.nuxeo.ai.ai-model")
@Deploy("org.nuxeo.elasticsearch.core.test:elasticsearch-test-contrib.xml")
public class DatasetExportTest {

    public static final String TEST_MIME_TYPE = "text/plain";

    @Inject
    public BulkService service;

    @Inject
    public CoreSession session;

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected EventService eventService;

    @Inject
    protected PipelineService pipesService;

    @Inject
    protected WorkManager workManager;

    @Inject
    ElasticSearchAdmin esa;

    @Test
    public void testBulkExport() throws Exception {

        DocumentModel testRoot = setupTestData();

        Map<String, String> collector = new HashMap<>();
        pipesService.addEventListener(DATASET_EXPORT_DONE_EVENT, true, new CollectingDataSetDoneListener(collector));
        String nxql = String.format("SELECT * from Document where ecm:parentId='%s'", testRoot.getId());
        String commandId = Framework.getService(DatasetExportService.class)
                                    .export(session, nxql,
                                            Arrays.asList("dc:title", "file:content"),
                                            Arrays.asList("dc:description"), 60);
        assertTrue("Bulk action didn't finish", service.await(commandId, Duration.ofSeconds(30)));

        BulkStatus status = service.getStatus(commandId);
        assertNotNull(status);
        assertEquals(COMPLETED, status.getState());
        assertEquals(500, status.getProcessed());

        eventService.waitForAsyncCompletion();
        // We wait for the eventService to complete but still it sometimes fails, so I added a little extra time for
        // the async listener to make sure its reliable.
        Thread.sleep(100L);
        assertEquals(2, collector.size());
        int trainingCount = countBlobRecords(commandId, TRAINING_COMPUTATION, collector);
        int validationCount = countBlobRecords(commandId, VALIDATION_COMPUTATION, collector);
        assertTrue(trainingCount > validationCount);
        // 50 null records have been discarded so we are left with 450 entries, split roughly 60 to 40 %
        assertEquals(450, trainingCount + validationCount);
    }

    /**
     * Wait for async worker completion then wait for indexing completion
     */
    public void waitForCompletion() throws Exception {
        workManager.awaitCompletion(20, TimeUnit.SECONDS);
        esa.prepareWaitForIndexing().get(20, TimeUnit.SECONDS);
        esa.refresh();
    }

    @NotNull
    protected DocumentModel setupTestData() {
        DocumentModel testRoot = session.createDocumentModel("/", "bulkexporttest", "Folder");
        testRoot = session.createDocument(testRoot);
        session.saveDocument(testRoot);

        DocumentModel test = session.getDocument(testRoot.getRef());

        for (int i = 0; i < 500; ++i) {
            DocumentModel doc = session.createDocumentModel(test.getPathAsString(), "doc" + i, "File");
            doc.setPropertyValue("dc:title", "doc_" + i % 2);
            doc.setPropertyValue("dc:description", "desc" + i % 4);
            if (i % 2 == 0) {
                doc.setPropertyValue("dc:language", "en" + i);
            }
            if (i % 10 != 0) {
                Blob blob = Blobs.createBlob("My text" + i, TEST_MIME_TYPE);
                doc.setPropertyValue("file:content", (Serializable) blob);
            }
            session.createDocument(doc);
        }

        txFeature.nextTransaction();
        return testRoot;
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testStats() throws Exception {
        DocumentModel testRoot = setupTestData();
        waitForCompletion();
        String nxql = String.format("SELECT * from Document where ecm:parentId='%s'", testRoot.getId());
        Collection<Statistic> statistics = Framework.getService(DatasetStatsService.class)
                                                    .getStatistics(session, nxql,
                                                                   Arrays.asList("dc:title", "file:content"),
                                                                   Arrays.asList("dc:description", "dc:language"));
        assertEquals("There should be 3 aggregates * 3 text fields + 2 agg content field + 2 totals = 13",
                     13, statistics.size());
        Map<String, List<Statistic>> byType = statistics.stream().collect(groupingBy(Statistic::getType));
        Map<String, List<Statistic>> byField = statistics.stream().collect(groupingBy(Statistic::getField));
        assertEquals("There should be 3 aggregates + 2 total = 5", 5, byType.size());
        Statistic total = byType.get(STATS_TOTAL).get(0);
        assertEquals(500, total.getNumericValue().intValue());
        total = byType.get(STATS_COUNT).get(0);
        assertEquals("There are 200 rows where all fields are not null.", 200, total.getNumericValue().intValue());
        assertEquals("There should be 4 fields + 2 total = 6", 6, byField.size());
        Statistic cardDesc = byType.get(AGG_CARDINALITY).stream()
                                   .filter(a -> "dc:description".equals(a.getField())).findFirst().get();
        assertEquals(2, cardDesc.getNumericValue().intValue());
        Statistic termDesc = byType.get(AGG_TYPE_TERMS).stream()
                                   .filter(a -> "dc:description".equals(a.getField())).findFirst().get();
        Statistic missingLang = byType.get(AGG_MISSING).stream()
                                      .filter(a -> "dc:language".equals(a.getField())).findFirst().get();
        assertEquals(250, missingLang.getNumericValue().intValue());
        Statistic missingContent = byType.get(AGG_MISSING).stream()
                                         .filter(a -> "file:content.digest".equals(a.getField())).findFirst().get();
        assertEquals(50, missingContent.getNumericValue().intValue());

    }

    protected int countBlobRecords(String commandId, String actionData, Map<String, String> collector) throws IOException {
        String blobRef = collector.get(makeKey(commandId, actionData));
        Blob blob = getBlobFromProvider(Framework.getService(BlobManager.class).getBlobProvider("test"), blobRef);
        assertNotNull(blob);
        return countNumberOfExamples(blob, 3);
    }
}
