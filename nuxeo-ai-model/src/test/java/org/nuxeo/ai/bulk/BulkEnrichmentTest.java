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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.bulk.message.BulkStatus.State.COMPLETED;

import java.io.IOException;
import java.time.Duration;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

@RunWith(FeaturesRunner.class)
@Features({ EnrichmentTestFeature.class, AutomationFeature.class, PlatformFeature.class, CoreBulkFeature.class,
        CoreFeature.class })
@Deploy({ "org.nuxeo.ai.ai-model", "org.nuxeo.ai.ai-model:OSGI-INF/model-serving-test.xml" })
public class BulkEnrichmentTest {

    public static final int NUM_OF_DOCS = 100;

    @Inject
    public BulkService bulkService;

    @Inject
    public CoreSession session;

    @Inject
    protected TransactionalFeature txFeature;

    public DocumentModel setupTestData() throws IOException {
        DocumentModel testRoot = session.createDocumentModel("/", "bulkenrichtest", "Folder");
        testRoot = session.createDocument(testRoot);
        session.saveDocument(testRoot);

        DocumentModel test = session.getDocument(testRoot.getRef());
        for (int i = 0; i < NUM_OF_DOCS; ++i) {
            DocumentModel doc = session.createDocumentModel(test.getPathAsString(), "doc" + i, "File");
            doc.setPropertyValue("dc:title", "doc_" + i % 2);
            session.createDocument(doc);
        }

        txFeature.nextTransaction();
        return testRoot;
    }

    @Test
    public void testBulkEnrich() throws Exception {

        DocumentModel testRoot = setupTestData();
        String nxql = String.format("SELECT * from Document where ecm:parentId='%s'", testRoot.getId());
        BulkCommand command = new BulkCommand.Builder(BulkEnrichmentAction.ACTION_NAME, nxql)
                .user(session.getPrincipal().getName())
                .repository(session.getRepositoryName())
                .build();
        bulkService.submit(command);
        assertTrue("Bulk action didn't finish", bulkService.await(command.getId(), Duration.ofSeconds(60)));

        BulkStatus status = bulkService.getStatus(command.getId());
        assertEquals(COMPLETED, status.getState());
        assertEquals(NUM_OF_DOCS, status.getProcessed());
        assertEquals(NUM_OF_DOCS, status.getTotal());
    }
}
