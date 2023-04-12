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

import javax.inject.Inject;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.AIConstants.AUTO;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_ITEMS;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_SCHEMA_NAME;
import static org.nuxeo.ai.auto.AutoService.AUTO_ACTION.ALL;
import static org.nuxeo.ai.auto.AutoService.AUTO_ACTION.CORRECT;
import static org.nuxeo.ai.auto.AutoService.AUTO_ACTION.FILL;
import static org.nuxeo.ai.enrichment.TestDocMetadataService.setupTestEnrichmentMetadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.nuxeo.ai.auto.AutoHistory;
import org.nuxeo.ai.auto.AutoPropertiesOperation;
import org.nuxeo.ai.auto.AutoService;
import org.nuxeo.ai.metadata.SuggestionMetadataWrapper;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ai.services.DocMetadataService;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.EventServiceAdmin;
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

    @Inject
    protected EventServiceAdmin esa;

    @Test
    @Deploy("org.nuxeo.ai.ai-core:OSGI-INF/core-types-test.xml")
    @Deploy("org.nuxeo.ai.ai-core:OSGI-INF/auto-config-test2.xml")
    public void shouldWriteAutoEnrichment() {
        DocumentModel testDoc = session.createDocumentModel("/", "My Auto Doc", "MultiFile");
        testDoc.addFacet(ENRICHMENT_FACET);
        HashMap<String, String> properties = new HashMap<>();
        properties.put("xpath", "dc:title");
        properties.put("model", "Model Name");
        testDoc.setProperty(ENRICHMENT_SCHEMA_NAME, AUTO.FILLED.lowerName(), Collections.singletonList(properties));
        testDoc = session.createDocument(testDoc);

        txFeature.nextTransaction();

        testDoc = session.getDocument(testDoc.getRef());
        @SuppressWarnings("unchecked")
        List<Map<String, String>> property = (List<Map<String, String>>) testDoc.getProperty(ENRICHMENT_SCHEMA_NAME,
                AUTO.FILLED.lowerName());
        assertThat(property).isNotEmpty()
                            .hasSize(1);
        Map<String, String> pred = property.get(0);
        assertThat(pred.get("xpath")).isEqualTo("dc:title");
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-core:OSGI-INF/core-types-test.xml")
    @Deploy("org.nuxeo.ai.ai-core:OSGI-INF/auto-config-test2.xml")
    public void testAutoAll() {
        DocumentModel testDoc = session.createDocumentModel("/", "My Auto Doc", "MultiFile");
        testDoc = session.createDocument(testDoc);
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        esa.setListenerEnabledFlag("enrichedDirtyProp", false);
        esa.setListenerEnabledFlag("enrichmentModified", false);
        try {
            EnrichmentMetadata suggestionMetadata = setupTestEnrichmentMetadata(testDoc);
            testDoc = docMetadataService.saveEnrichment(session, suggestionMetadata);
            session.saveDocument(testDoc);
            txFeature.nextTransaction();

            autoService.calculateProperties(testDoc, ALL);
            testDoc = session.saveDocument(testDoc);

            assertThat((String[]) testDoc.getPropertyValue("complexTest:testList")).hasSize(3);
        } finally {
            esa.setListenerEnabledFlag("enrichedDirtyProp", true);
            esa.setListenerEnabledFlag("enrichmentModified", true);
        }

    }

    @Test
    @Deploy("org.nuxeo.ai.ai-core:OSGI-INF/core-types-test.xml")
    @Deploy("org.nuxeo.ai.ai-core:OSGI-INF/auto-config-test.xml")
    public void testAutofill() {
        DocumentModel testDoc = session.createDocumentModel("/", "My Auto Doc", "MultiFile");
        testDoc = session.createDocument(testDoc);
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        for (String schema : testDoc.getSchemas()) {
            for (Map.Entry<String, Object> entry : testDoc.getProperties(schema)
                                                          .entrySet()) {
                System.out.println(schema + " prop " + entry.getKey() + " is list " + testDoc.getPropertyObject(schema,
                                                                                                     entry.getKey())
                                                                                             .isList());
            }
        }

        EnrichmentMetadata suggestionMetadata = setupTestEnrichmentMetadata(testDoc);
        testDoc = docMetadataService.saveEnrichment(session, suggestionMetadata);
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(testDoc);
        assertTrue(wrapper.getModels()
                          .contains("stest"));
        assertTrue("dc:title must be auto filled.", wrapper.isAutoFilled("dc:title"));
        assertTrue("dc:format must be auto filled.", wrapper.isAutoFilled("dc:format"));
        assertTrue("complexTest:testList must be auto filled.", wrapper.isAutoFilled("complexTest:testList"));

        assertFalse("Doesn't have a human value", wrapper.hasHumanValue("dc:title"));
        assertFalse("Doesn't have a human value", wrapper.hasHumanValue("dc:format"));
        assertFalse("Doesn't have a human value", wrapper.hasHumanValue("complexTest:testList"));

        String[] list = (String[]) testDoc.getPropertyValue("complexTest:testList");
        assertThat(list).hasSize(3);

        autoService.calculateProperties(testDoc, FILL);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        wrapper = new SuggestionMetadataWrapper(testDoc);
        assertTrue("dc:title must be auto filled.", wrapper.isAutoFilled("dc:title"));
        assertTrue("dc:format must be auto filled.", wrapper.isAutoFilled("dc:format"));
        assertFalse("Property hasn't been AutoCorrected.", wrapper.isAutoCorrected("dc:title"));
        assertFalse("Property hasn't been AutoCorrected.", wrapper.isAutoCorrected("dc:format"));
        assertEquals("cat", testDoc.getPropertyValue("dc:format"));

        docMetadataService.resetAuto(testDoc, AUTO.FILLED, "dc:format", true);
        wrapper = new SuggestionMetadataWrapper(testDoc);
        assertNull("The property must be reset to old value.", testDoc.getPropertyValue("dc:format"));
        assertFalse("Property is no longer Auto filled.", wrapper.isAutoFilled("dc:format"));
        assertTrue("dc:title must be auto filled.", wrapper.isAutoFilled("dc:title"));
        assertFalse("dc:title hasn't been AutoCorrected.", wrapper.isAutoCorrected("dc:title"));

        // Now test mutual exclusivity
        autoService.calculateProperties(testDoc, CORRECT);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        wrapper = new SuggestionMetadataWrapper(testDoc);
        assertTrue("dc:title must be Auto Filled.", wrapper.isAutoFilled("dc:title"));
        assertTrue("dc:format must be AutoCorrected.", wrapper.isAutoCorrected("dc:format"));
        assertFalse("Won't be autocorrected because its been autofilled.", wrapper.isAutoCorrected("dc:title"));
        testDoc.setPropertyValue("dc:title", "title again");
        autoService.autoApproveDirtyProperties(testDoc);
        wrapper = new SuggestionMetadataWrapper(testDoc);
        assertFalse("dc:title must no longer by autofilled.", wrapper.isAutoFilled("dc:title"));
        assertTrue("dc:format must be AutoCorrected.", wrapper.isAutoCorrected("dc:format"));
        assertTrue(wrapper.getSuggestionsByProperty("dc:title")
                          .isEmpty());
        assertFalse(wrapper.getSuggestionsByProperty("dc:format")
                           .isEmpty());
        assertTrue(wrapper.hasHumanValue("dc:title"));
        assertFalse(wrapper.hasHumanValue("dc:format"));

        autoService.approveAutoProperty(testDoc, "dc:format");
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        wrapper = new SuggestionMetadataWrapper(testDoc);
        assertFalse("dc:format must no longer by AutoCorrected.", wrapper.isAutoCorrected("dc:format"));
        assertTrue(wrapper.getSuggestionsByProperty("dc:format")
                          .isEmpty());
        assertTrue(wrapper.hasHumanValue("dc:format"));
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-core:OSGI-INF/auto-config-test.xml")
    public void testAutoCorrect() {
        String formatText = "something";
        DocumentModel testDoc = session.createDocumentModel("/", "My Auto Corrected Doc", "File");
        testDoc = session.createDocument(testDoc);
        testDoc.setPropertyValue("dc:title", "my title");
        testDoc.setPropertyValue("dc:format", formatText);
        session.saveDocument(testDoc);
        txFeature.nextTransaction();
        SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(testDoc);
        assertTrue(wrapper.hasHumanValue("dc:title"));
        assertTrue(wrapper.hasHumanValue("dc:format"));
        testDoc = docMetadataService.saveEnrichment(session, setupTestEnrichmentMetadata(testDoc));
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        wrapper = new SuggestionMetadataWrapper(testDoc);
        assertFalse("Property hasn't been AutoCorrected.", wrapper.isAutoCorrected("dc:title"));
        assertTrue("dc:format must be AutoCorrected.", wrapper.isAutoCorrected("dc:format"));
        assertFalse("Won't be autofilled because its been autocorrected.", wrapper.isAutoFilled("dc:title"));
        assertFalse("Won't be autofilled because its been autocorrected", wrapper.isAutoFilled("dc:format"));
        assertEquals("cat", testDoc.getPropertyValue("dc:format"));
        List<AutoHistory> history = docMetadataService.getAutoHistory(testDoc);
        assertEquals(1, history.size());
        assertEquals(formatText, history.get(0)
                                        .getPreviousValue());

        // Manipulate the test data so the suggestion are removed
        testDoc.setProperty(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS, null);

        // Run correct again
        autoService.calculateProperties(testDoc, CORRECT);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        wrapper = new SuggestionMetadataWrapper(testDoc);
        assertEquals("The property must be reset to old value.", formatText, testDoc.getPropertyValue("dc:format"));
        assertFalse("Property is no longer AutoCorrected.", wrapper.isAutoCorrected("dc:format"));
        history = docMetadataService.getAutoHistory(testDoc);
        assertTrue(wrapper.getAutoProperties()
                          .isEmpty());
        assertTrue(history.isEmpty());
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-core:OSGI-INF/auto-config-test.xml")
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

        Blob histBlob = (Blob) testDoc.getProperty(ENRICHMENT_SCHEMA_NAME, AUTO.HISTORY.lowerName());
        assertNotNull(histBlob);
        assertThat(histBlob.getFilename()).isNotEmpty()
                                          .endsWith(".json");

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
    @Deploy("org.nuxeo.ai.ai-core:OSGI-INF/auto-config-test.xml")
    public void testUpdateAutoHistory() {
        String comment = "No Comment";
        DocumentModel testDoc = session.createDocumentModel("/", "My Auto Hist Doc", "File");
        testDoc = session.createDocument(testDoc);
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        List<AutoHistory> history = docMetadataService.getAutoHistory(testDoc);
        assertTrue(history.isEmpty());
        docMetadataService.updateAuto(testDoc, AUTO.FILLED, "dc:title", "unknown", null, comment);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        history = docMetadataService.getAutoHistory(testDoc);
        SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(testDoc);
        assertTrue("History must be empty because there is no old value.", history.isEmpty());
        assertTrue("dc:title was auto filled with no history.", wrapper.isAutoFilled("dc:title"));

        docMetadataService.updateAuto(testDoc, AUTO.FILLED, "dc:title", "unknown", "I_AM_OLD", comment);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        history = docMetadataService.getAutoHistory(testDoc);
        assertEquals(1, history.size());

        docMetadataService.updateAuto(testDoc, AUTO.FILLED, "dc:title", "", "NOT_OLD", comment);
        docMetadataService.updateAuto(testDoc, AUTO.FILLED, "dc:format", "", "OLD", comment);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        history = docMetadataService.getAutoHistory(testDoc);
        assertEquals(2, history.size());

        docMetadataService.updateAuto(testDoc, AUTO.FILLED, "dc:format", "", "OLDISH", comment);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        history = docMetadataService.getAutoHistory(testDoc);

        // We have updated dc:title and dc:format twice but we should have only the 2 latest entries in the history.
        assertEquals(2, history.size());
        assertEquals("NOT_OLD",
                history.stream()
                       .filter(h -> "dc:title".equals(h.getProperty()))
                       .findFirst()
                       .get()
                       .getPreviousValue());
        assertEquals("OLDISH",
                history.stream()
                       .filter(h -> "dc:format".equals(h.getProperty()))
                       .findFirst()
                       .get()
                       .getPreviousValue());

    }

    @Test
    @Deploy("org.nuxeo.ai.ai-core:OSGI-INF/auto-config-test.xml")
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
        SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(testDoc);
        assertTrue("dc:title must be AutoFilled.", wrapper.isAutoFilled("dc:title"));
        assertTrue("dc:format must be AutoFilled.", wrapper.isAutoFilled("dc:format"));
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-core:OSGI-INF/auto-config-test.xml")
    public void testhasValue() {
        DocumentModel testDoc = session.createDocumentModel("/", "My Auto Value Doc", "File");
        testDoc = session.createDocument(testDoc);
        session.saveDocument(testDoc);
        txFeature.nextTransaction();
        SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(testDoc);
        assertFalse(wrapper.hasHumanValue("dc:title"));

        testDoc.setPropertyValue("dc:title", "");
        wrapper = new SuggestionMetadataWrapper(testDoc);
        assertFalse("Must allow empty strings", wrapper.hasHumanValue("dc:title"));

        testDoc.setPropertyValue("dc:title", "my title");
        wrapper = new SuggestionMetadataWrapper(testDoc);
        assertTrue(wrapper.hasHumanValue("dc:title"));

        wrapper = new SuggestionMetadataWrapper(testDoc);
        assertFalse("Must allow empty strings[]", wrapper.hasHumanValue("dc:subjects"));

        testDoc.setPropertyValue("dc:subjects", new String[]{"sciences", "art/cinema"});
        wrapper = new SuggestionMetadataWrapper(testDoc);
        assertTrue(wrapper.hasHumanValue("dc:subjects"));

        wrapper = new SuggestionMetadataWrapper(testDoc);
        assertFalse("Must allow empty dates", wrapper.hasHumanValue("dc:issued"));
    }
}
