package org.nuxeo.ai.bulk;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_SCHEMA_NAME;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;
import static org.nuxeo.ecm.core.bulk.message.BulkStatus.State.COMPLETED;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.nuxeo.ai.AIConstants.AUTO;
import org.nuxeo.ai.auto.AutoHistory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import com.fasterxml.jackson.core.type.TypeReference;

public class BaseBulkEnrich {

    public static final int NUM_OF_DOCS = 100;

    @Inject
    private CoreSession session;

    @Inject
    private BulkService bulkService;

    @Inject
    private TransactionalFeature txFeature;

    @Before
    public void setup() {
        setupTestData();
    }

    @After
    public void destroy() {
        session.removeChildren(new PathRef("/"));
    }

    public DocumentModel getRoot() {
        return session.getDocument(new PathRef("/bulkenrichtest"));
    }

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

    protected List<AutoHistory> getAutoHistories(DocumentModel workingDoc) throws java.io.IOException {
        Blob autoBlob = (Blob) workingDoc.getProperty(ENRICHMENT_SCHEMA_NAME, AUTO.HISTORY.lowerName());
        assertThat(autoBlob).isNotNull();

        TypeReference<List<AutoHistory>> HISTORY_TYPE = new TypeReference<List<AutoHistory>>() {
        };
        List<AutoHistory> autoHistories = MAPPER.readValue(autoBlob.getByteArray(), HISTORY_TYPE);
        assertThat(autoHistories).isNotEmpty();
        return autoHistories;
    }
}
