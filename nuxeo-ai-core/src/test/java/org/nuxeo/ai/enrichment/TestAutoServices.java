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
package org.nuxeo.ai.enrichment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.AIConstants.AUTO_CORRECTED;
import static org.nuxeo.ai.AIConstants.AUTO_FILLED;
import static org.nuxeo.ai.auto.AutoService.AUTO_ACTION.ALL;
import static org.nuxeo.ai.auto.AutoService.AUTO_ACTION.CORRECT;
import static org.nuxeo.ai.auto.AutoService.AUTO_ACTION.FILL;
import static org.nuxeo.ai.enrichment.TestDocMetadataService.setupTestEnrichmentMetadata;

import java.util.Collections;
import java.util.List;
import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.auto.AutoHistory;
import org.nuxeo.ai.auto.AutoPropertiesOperation;
import org.nuxeo.ai.auto.AutoService;
import org.nuxeo.ai.metadata.SuggestionMetadataAdapter;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ai.services.DocMetadataService;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, AutomationFeature.class, PlatformFeature.class})
@Deploy({"org.nuxeo.ai.ai-core"})
public class TestAutoServices {
    @Inject
    protected CoreSession session;

    @Inject
    protected AIComponent aiComponent;

    @Inject
    protected DocMetadataService docMetadataService;

    @Inject
    protected AutoService autoService;

    @Inject
    protected AutomationService automationService;

    @Inject
    protected TransactionalFeature txFeature;

    @Test
    public void testAutofill() {
        assertNotNull(docMetadataService);
        DocumentModel testDoc = session.createDocumentModel("/", "My Auto Doc", "File");
        testDoc = session.createDocument(testDoc);
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        EnrichmentMetadata suggestionMetadata = setupTestEnrichmentMetadata(testDoc);
        testDoc = docMetadataService.saveEnrichment(session, suggestionMetadata);
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        SuggestionMetadataAdapter adapted = testDoc.getAdapter(SuggestionMetadataAdapter.class);
        assertTrue(adapted.getModels().contains("stest"));
        assertFalse("Property hasn't been autofilled yet.", adapted.isAutoFilled("dc:title"));
        assertFalse("Property hasn't been autofilled yet.", adapted.isAutoFilled("dc:format"));
        assertFalse("Doesn't have a human value because its null", adapted.hasHumanValue("dc:title"));
        assertFalse("Desn't have a human value because its null", adapted.hasHumanValue("dc:format"));
        autoService.calculateProperties(testDoc, FILL);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        adapted = testDoc.getAdapter(SuggestionMetadataAdapter.class);
        assertTrue("dc:title must be auto filled.", adapted.isAutoFilled("dc:title"));
        assertTrue("dc:format must be auto filled.", adapted.isAutoFilled("dc:format"));
        assertFalse(adapted.hasHumanValue("dc:title"));
        assertFalse(adapted.hasHumanValue("dc:format"));
        //Call it again to check there are no side effects
        autoService.calculateProperties(testDoc, FILL);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        adapted = testDoc.getAdapter(SuggestionMetadataAdapter.class);
        assertTrue("dc:title must be auto filled.", adapted.isAutoFilled("dc:title"));
        assertTrue("dc:format must be auto filled.", adapted.isAutoFilled("dc:format"));
        assertFalse("Property hasn't been AutoCorrected.", adapted.isAutoCorrected("dc:title"));
        assertFalse("Property hasn't been AutoCorrected.", adapted.isAutoCorrected("dc:format"));
        assertEquals("cat", testDoc.getPropertyValue("dc:format"));

        docMetadataService.resetAuto(testDoc, AUTO_FILLED, "dc:format", true);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        adapted = testDoc.getAdapter(SuggestionMetadataAdapter.class);
        assertNull("The property must be reset to old value.", testDoc.getPropertyValue("dc:format"));
        assertFalse("Property is no longer Auto filled.", adapted.isAutoFilled("dc:format"));
        assertTrue("dc:title must be auto filled.", adapted.isAutoFilled("dc:title"));
        assertFalse("dc:title hasn't been AutoCorrected.", adapted.isAutoCorrected("dc:title"));

        // Now test mutual exclusivity
        autoService.calculateProperties(testDoc, CORRECT);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        adapted = testDoc.getAdapter(SuggestionMetadataAdapter.class);
        assertTrue("dc:title must be AutoCorrected.", adapted.isAutoCorrected("dc:title"));
        assertFalse("Won't be autofilled because its been autocorrected.", adapted.isAutoFilled("dc:title"));

        autoService.approveAutoProperty(testDoc, "dc:title");
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        adapted = testDoc.getAdapter(SuggestionMetadataAdapter.class);
        assertFalse("dc:title must no longer by AutoCorrected.", adapted.isAutoCorrected("dc:title"));
        assertTrue("dc:format must be AutoCorrected.", adapted.isAutoCorrected("dc:format"));
        assertTrue(adapted.getSuggestionsByProperty("dc:title").isEmpty());
        assertFalse(adapted.getSuggestionsByProperty("dc:format").isEmpty());
        assertTrue(adapted.hasHumanValue("dc:title"));
        assertFalse(adapted.hasHumanValue("dc:format"));

        autoService.approveAutoProperty(testDoc, "dc:format");
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        adapted = testDoc.getAdapter(SuggestionMetadataAdapter.class);
        assertFalse("dc:format must no longer by AutoCorrected.", adapted.isAutoCorrected("dc:format"));
        assertTrue(adapted.getSuggestionsByProperty("dc:format").isEmpty());
        assertTrue(adapted.hasHumanValue("dc:format"));
    }

    @Test
    public void testAutoCorrect() {
        DocumentModel testDoc = session.createDocumentModel("/", "My Auto Corrected Doc", "File");
        testDoc = session.createDocument(testDoc);
        testDoc = docMetadataService.saveEnrichment(session, setupTestEnrichmentMetadata(testDoc));
        testDoc.setPropertyValue("dc:title", "my title");
        testDoc.setPropertyValue("dc:format", "something");
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        SuggestionMetadataAdapter adapted = testDoc.getAdapter(SuggestionMetadataAdapter.class);
        assertTrue(adapted.hasHumanValue("dc:title"));
        assertTrue(adapted.hasHumanValue("dc:format"));
        assertFalse("Property hasn't been AutoCorrected.", adapted.isAutoCorrected("dc:title"));

        autoService.calculateProperties(testDoc, ALL);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        adapted = testDoc.getAdapter(SuggestionMetadataAdapter.class);
        assertTrue("dc:title must be AutoCorrected.", adapted.isAutoCorrected("dc:title"));
        assertTrue("dc:format must be AutoCorrected.", adapted.isAutoCorrected("dc:format"));
        assertFalse("Won't be autofilled because its been autocorrected.", adapted.isAutoFilled("dc:title"));
        assertFalse("Won't be autofilled because its been autocorrected", adapted.isAutoFilled("dc:format"));
        assertEquals("girl", testDoc.getPropertyValue("dc:title"));

        docMetadataService.resetAuto(testDoc, AUTO_CORRECTED, "dc:title", true);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        adapted = testDoc.getAdapter(SuggestionMetadataAdapter.class);
        assertEquals("The property must be reset to old value.", "my title", testDoc.getPropertyValue("dc:title"));
        assertFalse("Property is no longer AutoCorrected.", adapted.isAutoCorrected("dc:title"));
        assertTrue("dc:format must be AutoCorrected.", adapted.isAutoCorrected("dc:format"));
        List<AutoHistory> history = docMetadataService.getAutoHistory(testDoc);
        assertEquals(1, history.size());
        // Call it again but it shouldn't do anything because we already reset the value
        docMetadataService.resetAuto(testDoc, AUTO_CORRECTED, "dc:title", true);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        assertEquals("The property must be reset to old value.", "my title", testDoc.getPropertyValue("dc:title"));
    }

    @Test
    public void testAutoHistory() {
        DocumentModel testDoc = session.createDocumentModel("/", "My Auto History Doc", "File");
        testDoc = session.createDocument(testDoc);
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        List<AutoHistory> history = docMetadataService.getAutoHistory(testDoc);
        assertTrue(history.isEmpty());
        AutoHistory hist = new AutoHistory("dc:title", "old");
        docMetadataService.setAutoHistory(testDoc, Collections.singletonList(hist));
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        history = docMetadataService.getAutoHistory(testDoc);
        assertEquals(1, history.size());
        assertEquals("History must have been saved and returned correctly.", hist, history.get(0));

        history.add(new AutoHistory("dc:format", "old Value"));
        docMetadataService.setAutoHistory(testDoc, history);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        history = docMetadataService.getAutoHistory(testDoc);
        assertEquals(2, history.size());
    }

    @Test
    public void testUpdateAutoHistory() {
        String comment = "No Comment";
        DocumentModel testDoc = session.createDocumentModel("/", "My Auto Hist Doc", "File");
        testDoc = session.createDocument(testDoc);
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        List<AutoHistory> history = docMetadataService.getAutoHistory(testDoc);
        assertTrue(history.isEmpty());
        docMetadataService.updateAuto(testDoc, AUTO_FILLED, "dc:title", null, comment);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        history = docMetadataService.getAutoHistory(testDoc);
        SuggestionMetadataAdapter adapted = testDoc.getAdapter(SuggestionMetadataAdapter.class);
        assertTrue("History must be empty because there is no old value.", history.isEmpty());
        assertTrue("dc:title was auto filled with no history.", adapted.isAutoFilled("dc:title"));

        docMetadataService.updateAuto(testDoc, AUTO_FILLED, "dc:title", "I_AM_OLD", comment);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        history = docMetadataService.getAutoHistory(testDoc);
        assertEquals(1, history.size());

        docMetadataService.updateAuto(testDoc, AUTO_FILLED, "dc:title", "NOT_OLD", comment);
        docMetadataService.updateAuto(testDoc, AUTO_FILLED, "dc:format", "OLD", comment);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        history = docMetadataService.getAutoHistory(testDoc);
        assertEquals(2, history.size());

        docMetadataService.updateAuto(testDoc, AUTO_FILLED, "dc:format", "OLDISH", comment);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        history = docMetadataService.getAutoHistory(testDoc);

        // We have updated dc:title and dc:format twice but we should have only the 2 latest entries in the history.
        assertEquals(2, history.size());
        assertEquals("NOT_OLD", history.stream()
                                       .filter(h -> "dc:title".equals(h.getProperty()))
                                       .findFirst().get().getPreviousValue());
        assertEquals("OLDISH", history.stream()
                                      .filter(h -> "dc:format".equals(h.getProperty()))
                                      .findFirst().get().getPreviousValue());

    }

    @Test
    public void testAutoOperation() throws OperationException {
        // Setup doc with suggestions
        DocumentModel testDoc = session.createDocumentModel("/", "My Auto Op Doc", "File");
        testDoc = session.createDocument(testDoc);
        testDoc = docMetadataService.saveEnrichment(session, setupTestEnrichmentMetadata(testDoc));
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        // Call operation on the doc
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(testDoc);
        OperationChain chain = new OperationChain("testAutoChain1");
        chain.add(AutoPropertiesOperation.ID);
        DocumentModel opDoc = (DocumentModel) automationService.run(ctx, chain);
        opDoc = session.saveDocument(opDoc);
        txFeature.nextTransaction();
        SuggestionMetadataAdapter adapted = opDoc.getAdapter(SuggestionMetadataAdapter.class);
        assertTrue("dc:title must be AutoCorrected.", adapted.isAutoCorrected("dc:title"));
        assertTrue("dc:format must be AutoCorrected.", adapted.isAutoCorrected("dc:format"));
    }
}
