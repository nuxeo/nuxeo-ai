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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.bulk.CollectingDataSetDoneListener.makeKey;
import static org.nuxeo.ai.bulk.DataSetBulkAction.TRAINING_COMPUTATION_NAME;
import static org.nuxeo.ai.bulk.DataSetBulkAction.VALIDATION_COMPUTATION_NAME;
import static org.nuxeo.ai.bulk.DataSetExportStatusComputation.DATASET_EXPORT_DONE_EVENT;
import static org.nuxeo.ai.bulk.TensorTest.countNumberOfExamples;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.getBlobFromProvider;
import static org.nuxeo.ecm.core.bulk.message.BulkStatus.State.COMPLETED;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.model.export.DatasetExportService;
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
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.pipes.services.PipelineService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import javax.inject.Inject;
import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, AutomationFeature.class, PlatformFeature.class, CoreBulkFeature.class})
@Deploy("org.nuxeo.ai.ai-core:OSGI-INF/recordwriter-test.xml")
@Deploy("org.nuxeo.ai.ai-model")
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

    @Test
    public void testBulkExport() throws Exception {

        DocumentModel testRoot = session.createDocumentModel("/", "bulkexporttest", "Folder");
        testRoot = session.createDocument(testRoot);
        session.saveDocument(testRoot);

        DocumentModel test = session.getDocument(testRoot.getRef());

        for (int i = 0; i < 500; ++i) {
            DocumentModel doc = session.createDocumentModel(test.getPathAsString(), "doc" + i, "File");
            doc.setPropertyValue("dc:title", "doc" + i);
            doc.setPropertyValue("dc:description", "desc" + i % 2);
            // Add in a null property for testing
            if (i % 10 != 0) {
                Blob blob = Blobs.createBlob("My text" + i, TEST_MIME_TYPE);
                doc.setPropertyValue("file:content", (Serializable) blob);
            }
            session.createDocument(doc);
        }

        txFeature.nextTransaction();

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
        int trainingCount = countBlobRecords(commandId, TRAINING_COMPUTATION_NAME, collector);
        int validationCount = countBlobRecords(commandId, VALIDATION_COMPUTATION_NAME, collector);
        assertTrue(trainingCount > validationCount);
        // 50 null records have been discarded so we are left with 450 entries, split roughly 60 to 40 %
        assertEquals(450, trainingCount + validationCount);
    }

    protected int countBlobRecords(String commandId, String actionData, Map<String, String> collector) throws IOException {
        String blobRef = collector.get(makeKey(commandId, actionData));
        Blob blob = getBlobFromProvider(Framework.getService(BlobManager.class).getBlobProvider("test"), blobRef);
        assertNotNull(blob);
        return countNumberOfExamples(blob, 3);
    }
}
