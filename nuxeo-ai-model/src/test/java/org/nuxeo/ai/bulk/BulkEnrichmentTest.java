/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.Sets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.auto.AutoHistory;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.metadata.SuggestionMetadataWrapper;
import org.nuxeo.ai.model.export.DatasetExportService;
import org.nuxeo.ai.pipes.types.PropertyType;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.elasticsearch.test.RepositoryElasticSearchFeature;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.StreamService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import javax.inject.Inject;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.AIConstants.AUTO_HISTORY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_SCHEMA_NAME;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_JOB_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_TYPE;
import static org.nuxeo.ai.bulk.BulkRemoveEnrichmentAction.PARAM_MODEL;
import static org.nuxeo.ai.bulk.BulkRemoveEnrichmentAction.PARAM_XPATHS;
import static org.nuxeo.ai.enrichment.TestConfiguredStreamProcessors.waitForNoLag;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.CATEGORY_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TEXT_TYPE;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;
import static org.nuxeo.ecm.core.bulk.message.BulkStatus.State.COMPLETED;

@RunWith(FeaturesRunner.class)
@Features({ EnrichmentTestFeature.class, AutomationFeature.class, PlatformFeature.class, CoreBulkFeature.class,
        RepositoryElasticSearchFeature.class })
@Deploy("org.nuxeo.ai.ai-model")
@Deploy("org.nuxeo.ecm.platform.video.convert")
@Deploy("org.nuxeo.ecm.platform.video.core")
@Deploy("org.nuxeo.ai.ai-core")
@Deploy({ "org.nuxeo.ai.ai-core:OSGI-INF/recordwriter-test.xml", "org.nuxeo.ai.ai-model:OSGI-INF/bulk-test.xml" })
@Deploy("org.nuxeo.elasticsearch.core.test:elasticsearch-test-contrib.xml")
public class BulkEnrichmentTest {

    public static final int NUM_OF_DOCS = 100;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(5089);

    @Inject
    public BulkService bulkService;

    @Inject
    public CoreSession session;

    @Inject
    protected TransactionalFeature txFeature;

    public DocumentModel setupTestData() {
        DocumentModel testRoot = session.createDocumentModel("/", "bulkenrichtest", "Folder");
        testRoot = session.createDocument(testRoot);
        session.saveDocument(testRoot);

        DocumentModel test = session.getDocument(testRoot.getRef());
        for (int i = 0; i < NUM_OF_DOCS; ++i) {
            DocumentModel doc = session.createDocumentModel(test.getPathAsString(), "doc" + i, "File");
            doc.setPropertyValue("dc:title", "doc_" + i % 2);
            if (i % 5 == 0) {
                doc.setPropertyValue("dc:language", "en" + i);
            }
            session.createDocument(doc);
        }

        txFeature.nextTransaction();
        return testRoot;
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void testBulkEnrich() throws Exception {
        DocumentModel testRoot = setupTestData();
        String nxql = String.format("SELECT * from Document WHERE ecm:parentId='%s' AND ecm:primaryType = 'File'",
                testRoot.getId());
        BulkCommand command = new BulkCommand.Builder(BulkEnrichmentAction.ACTION_NAME, nxql).user(
                session.getPrincipal().getName()).repository(session.getRepositoryName()).build();
        submitAndAssert(command);

        LogManager manager = Framework.getService(StreamService.class).getLogManager("bulk");
        waitForNoLag(manager, "enrichment.in", "enrichment.in$SaveEnrichmentFunction", Duration.ofSeconds(5));
        txFeature.nextTransaction();

        List<DocumentModel> docs = getSomeDocuments(nxql);
        for (DocumentModel aDoc : docs) {
            assertTrue(aDoc.hasFacet(ENRICHMENT_FACET));
            SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(aDoc);
            assertEquals("report", aDoc.getPropertyValue("dc:nature"));
            assertEquals("me", aDoc.getPropertyValue("dc:creator"));
            assertEquals(2, wrapper.getModels().size());
        }

        // Call with Model param
        BulkCommand removed = new BulkCommand.Builder(BulkRemoveEnrichmentAction.ACTION_NAME, nxql).user(
                session.getPrincipal().getName())
                                                                                                   .repository(
                                                                                                           session.getRepositoryName())
                                                                                                   .param(PARAM_MODEL,
                                                                                                           "basicBulkModel")
                                                                                                   .build();
        submitAndAssert(removed);
        txFeature.nextTransaction();

        docs = getSomeDocuments(nxql);
        for (DocumentModel aDoc : docs) {
            SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(aDoc);
            assertEquals(1, wrapper.getModels().size());
            assertFalse("Title must be a suggestion", wrapper.getSuggestionsByProperty("dc:title").isEmpty());
            assertNull(aDoc.getPropertyValue("dc:nature"));
            assertEquals("Must be reset to previous value", "Administrator", aDoc.getPropertyValue("dc:creator"));
        }

        // Call with XPaths param
        removed = new BulkCommand.Builder(BulkRemoveEnrichmentAction.ACTION_NAME, nxql).user(
                session.getPrincipal().getName())
                                                                                       .repository(
                                                                                               session.getRepositoryName())
                                                                                       .param(PARAM_XPATHS,
                                                                                               (Serializable) Arrays.asList(
                                                                                                       "dc:title"))
                                                                                       .build();
        submitAndAssert(removed);
        txFeature.nextTransaction();

        docs = getSomeDocuments(nxql);
        for (DocumentModel aDoc : docs) {
            SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(aDoc);
            assertTrue("No long dc:title", wrapper.getSuggestionsByProperty("dc:title").isEmpty());
            assertEquals(1, wrapper.getModels().size());
        }

        // Call with no params
        removed = new BulkCommand.Builder(BulkRemoveEnrichmentAction.ACTION_NAME, nxql).user(
                session.getPrincipal().getName()).repository(session.getRepositoryName()).build();
        submitAndAssert(removed);
        txFeature.nextTransaction();

        docs = getSomeDocuments(nxql);
        for (DocumentModel aDoc : docs) {
            SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(aDoc);
            assertTrue(wrapper.getModels().isEmpty());
        }

        // All removed so lets add again.
        command = new BulkCommand.Builder(BulkEnrichmentAction.ACTION_NAME, nxql).user(session.getPrincipal().getName())
                                                                                 .repository(
                                                                                         session.getRepositoryName())
                                                                                 .build();
        submitAndAssert(command);
        waitForNoLag(manager, "enrichment.in", "enrichment.in$SaveEnrichmentFunction", Duration.ofSeconds(5));
        txFeature.nextTransaction();

        docs = getSomeDocuments(nxql);
        for (DocumentModel aDoc : docs) {
            SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(aDoc);
            assertEquals("you", aDoc.getPropertyValue("dc:title"));
            assertEquals(2, wrapper.getModels().size());
            assertEquals(4, wrapper.getAutoProperties().size());
        }
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void shouldNotBacktrackConfirmedValue() throws Exception {
        DocumentModel testRoot = setupTestData();
        String nxql = String.format("SELECT * from Document WHERE ecm:parentId='%s' AND ecm:primaryType = 'File'",
                testRoot.getId());
        BulkCommand command = new BulkCommand.Builder(BulkEnrichmentAction.ACTION_NAME, nxql).user(
                session.getPrincipal().getName()).repository(session.getRepositoryName()).build();
        submitAndAssert(command);

        LogManager manager = Framework.getService(StreamService.class).getLogManager("bulk");
        waitForNoLag(manager, "enrichment.in", "enrichment.in$SaveEnrichmentFunction", Duration.ofSeconds(5));
        txFeature.nextTransaction();

        List<DocumentModel> docs = getSomeDocuments(nxql);
        assertThat(docs).isNotEmpty();

        DocumentModel workingDoc = docs.get(0);
        assertThat((String) workingDoc.getPropertyValue("dc:title")).isEqualTo("you");
        List<AutoHistory> autoHistories = getAutoHistories(workingDoc);
        AutoHistory onTitle = autoHistories.stream()
                                           .filter(h -> h.getProperty().equals("dc:title"))
                                           .findFirst()
                                           .orElse(null);
        assertThat(autoHistories).hasSize(2);
        assertThat(onTitle).isNotNull();

        Property property = workingDoc.getProperty("dc:title");
        property.setValue("you");
        property.setForceDirty(true);

        session.saveDocument(workingDoc);
        txFeature.nextTransaction();

        BulkCommand removed = new BulkCommand.Builder(BulkRemoveEnrichmentAction.ACTION_NAME, nxql).user(
                session.getPrincipal().getName())
                                                                                                   .repository(
                                                                                                           session.getRepositoryName())
                                                                                                   .param(PARAM_MODEL,
                                                                                                           "descBulkModel")
                                                                                                   .build();
        submitAndAssert(removed);

        txFeature.nextTransaction();

        workingDoc = session.getDocument(workingDoc.getRef());
        assertThat(workingDoc.getPropertyValue("dc:title")).isEqualTo("you");
    }

    private List<AutoHistory> getAutoHistories(DocumentModel workingDoc) throws java.io.IOException {
        Blob autoBlob = (Blob) workingDoc.getProperty(ENRICHMENT_SCHEMA_NAME, AUTO_HISTORY);
        assertThat(autoBlob).isNotNull();

        TypeReference<List<AutoHistory>> HISTORY_TYPE = new TypeReference<List<AutoHistory>>() {
        };
        List<AutoHistory> autoHistories = MAPPER.readValue(autoBlob.getByteArray(), HISTORY_TYPE);
        assertThat(autoHistories).isNotEmpty();
        return autoHistories;
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void testBulkExportNoAutoFields() throws Exception {
        DocumentModel testRoot = setupTestData();
        String nxql = String.format("SELECT * from Document where ecm:primaryType = 'File' AND ecm:parentId='%s' ",
                testRoot.getId());
        String nxql_lang = nxql + "AND dc:language IS NOT NULL";
        LogManager manager = Framework.getService(StreamService.class).getLogManager("bulk");

        BulkCommand command = new BulkCommand.Builder(BulkEnrichmentAction.ACTION_NAME, nxql_lang).user(
                session.getPrincipal().getName()).repository(session.getRepositoryName()).build();

        DocumentModel fakeDE = session.createDocumentModel("/", "FakeDE", DATASET_EXPORT_TYPE);
        fakeDE.setPropertyValue(DATASET_EXPORT_JOB_ID, command.getId());
        session.createDocument(fakeDE);
        session.save();

        bulkService.submit(command);
        assertTrue("Bulk action didn't finish", bulkService.await(command.getId(), Duration.ofSeconds(30)));
        waitForNoLag(manager, "enrichment.in", "enrichment.in$SaveEnrichmentFunction", Duration.ofSeconds(5));
        txFeature.nextTransaction();

        DocumentModelList someDoc = session.query(nxql);
        long enriched = someDoc.stream().filter(doc -> doc.hasFacet(ENRICHMENT_FACET)).count();
        assertEquals(20, enriched);

        Set<PropertyType> input = Sets.newHashSet(new PropertyType("dc:title", CATEGORY_TYPE));
        Set<PropertyType> output = Sets.newHashSet(new PropertyType("dc:creator", TEXT_TYPE));

        DatasetExportService exportService = Framework.getService(DatasetExportService.class);
        String commandId = exportService.export(session, nxql, input, output, 60, null);

        assertTrue("Bulk action didn't finish", bulkService.await(commandId, Duration.ofSeconds(60)));
        BulkStatus status = bulkService.getStatus(commandId);
        assertEquals(COMPLETED, status.getState());
        assertEquals(100, status.getProcessed());
        // 20 were skipped because they were already auto-corrected
        assertEquals(20, status.getErrorCount());

        DocumentModelList datasetExports = exportService.getDatasetExports(session, commandId);
        assertThat(datasetExports).isNotEmpty();
    }

    protected void submitAndAssert(BulkCommand command) throws InterruptedException {
        bulkService.submit(command);
        assertTrue("Bulk action didn't finish", bulkService.await(command.getId(), Duration.ofSeconds(60)));
        BulkStatus status = bulkService.getStatus(command.getId());
        assertEquals(COMPLETED, status.getState());
        assertEquals(NUM_OF_DOCS, status.getProcessed());
    }

    protected List<DocumentModel> getSomeDocuments(String nxql) {
        DocumentModelList enriched = session.query(nxql, 20);
        // Choose some random documents and confirm they are enriched.
        List<DocumentModel> docs = new ArrayList<>();
        docs.add(enriched.get(14));
        docs.add(enriched.get(1));
        docs.add(enriched.get(9));
        docs.add(enriched.get(4));
        docs.add(enriched.get(16));
        return docs;
    }
}
