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
 *     anechaev
 */
package org.nuxeo.ai.enrichment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.AIConstants;
import org.nuxeo.ai.auto.AutoHistory;
import org.nuxeo.ai.auto.AutoService;
import org.nuxeo.ai.metadata.SuggestionMetadataWrapper;
import org.nuxeo.ai.services.DocMetadataService;
import org.nuxeo.ai.services.ModelUsageService;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Predicates;
import org.nuxeo.ecm.platform.audit.AuditFeature;
import org.nuxeo.ecm.platform.audit.api.AuditQueryBuilder;
import org.nuxeo.ecm.platform.audit.api.AuditReader;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.elasticsearch.test.RepositoryElasticSearchFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_ITEMS;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_SCHEMA_NAME;
import static org.nuxeo.ai.auto.AutoService.AUTO_ACTION.CORRECT;
import static org.nuxeo.ai.auto.AutoService.AUTO_ACTION.FILL;
import static org.nuxeo.ai.enrichment.TestDocMetadataService.setupTestEnrichmentMetadata;
import static org.nuxeo.ecm.platform.audit.api.BuiltinLogEntryData.LOG_CATEGORY;
import static org.nuxeo.ecm.platform.audit.api.BuiltinLogEntryData.LOG_EVENT_ID;

@RunWith(FeaturesRunner.class)
@Features({ EnrichmentTestFeature.class, AuditFeature.class, RepositoryElasticSearchFeature.class })
@Deploy({ "org.nuxeo.ai.ai-core" })
public class TestAutoServicesAudit {

    @Inject
    protected CoreSession session;

    @Inject
    protected DocMetadataService docMetadataService;

    @Inject
    protected AutoService autoService;

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected AuditReader auditReader;

    @Inject
    protected AuditFeature auditFeature;

    @Before
    public void reset() {
        session.removeChildren(new PathRef("/"));
        auditFeature.doClear();
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
            for (Map.Entry<String, Object> entry : testDoc.getProperties(schema).entrySet()) {
                System.out.println(schema + " prop " + entry.getKey() + " is list " + testDoc.getPropertyObject(schema,
                        entry.getKey()).isList());
            }
        }

        EnrichmentMetadata suggestionMetadata = setupTestEnrichmentMetadata(testDoc);
        testDoc = docMetadataService.saveEnrichment(session, suggestionMetadata);
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        autoService.calculateProperties(testDoc, FILL);
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(testDoc);
        assertTrue("dc:title must be auto filled.", wrapper.isAutoFilled("dc:title"));
        assertTrue("dc:format must be auto filled.", wrapper.isAutoFilled("dc:format"));
        assertFalse("Property hasn't been AutoCorrected.", wrapper.isAutoCorrected("dc:title"));
        assertFalse("Property hasn't been AutoCorrected.", wrapper.isAutoCorrected("dc:format"));
        assertEquals("cat", testDoc.getPropertyValue("dc:format"));

        AuditQueryBuilder qb = new AuditQueryBuilder();
        Predicate predicate = Predicates.eq(LOG_CATEGORY, "AI");
        qb.predicate(predicate).and(Predicates.eq(LOG_EVENT_ID, AIConstants.AUTO.FILLED.eventName()));
        List<LogEntry> logEntries = auditReader.queryLogs(qb);
        Set<LogEntry> perModelAudit = logEntries.stream()
                                                .filter(entry -> entry.getExtendedInfos()
                                                                      .get("model")
                                                                      .getValue(String.class)
                                                                      .equals("stest"))
                                                .collect(Collectors.toSet());
        assertThat(perModelAudit).hasSize(6);
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

        testDoc = docMetadataService.saveEnrichment(session, setupTestEnrichmentMetadata(testDoc));
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(testDoc);
        assertFalse("Property hasn't been AutoCorrected.", wrapper.isAutoCorrected("dc:title"));
        assertTrue("dc:format must be AutoCorrected.", wrapper.isAutoCorrected("dc:format"));
        assertFalse("Won't be autofilled because its been autocorrected.", wrapper.isAutoFilled("dc:title"));
        assertFalse("Won't be autofilled because its been autocorrected", wrapper.isAutoFilled("dc:format"));
        assertEquals("cat", testDoc.getPropertyValue("dc:format"));
        List<AutoHistory> history = docMetadataService.getAutoHistory(testDoc);
        assertEquals(1, history.size());
        assertEquals(formatText, history.get(0).getPreviousValue());

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
        assertTrue(wrapper.getAutoProperties().isEmpty());
        assertTrue(history.isEmpty());

        AuditQueryBuilder qb = new AuditQueryBuilder();
        Predicate predicate = Predicates.eq(LOG_CATEGORY, "AI");
        qb.predicate(predicate).and(Predicates.eq(LOG_EVENT_ID, AIConstants.AUTO.CORRECTED.eventName()));
        List<LogEntry> logEntries = auditReader.queryLogs(qb);
        Set<LogEntry> perModelAudit = logEntries.stream()
                                                .filter(entry -> entry.getExtendedInfos()
                                                                      .get("model")
                                                                      .getValue(String.class)
                                                                      .equals("stest"))
                                                .filter(entry -> entry.getExtendedInfos()
                                                                      .get("value")
                                                                      .getValue(Long.class)
                                                                      .equals(1L))
                                                .collect(Collectors.toSet());
        assertThat(perModelAudit).hasSize(1);
    }

    @Test
    @Deploy("org.nuxeo.elasticsearch.http.readonly")
    @Deploy("org.nuxeo.elasticsearch.audit.test:elasticsearch-audit-index-test-contrib.xml")
    @Deploy("org.nuxeo.ai.ai-core:OSGI-INF/core-types-test.xml")
    @Deploy("org.nuxeo.ai.ai-core:OSGI-INF/auto-config-test.xml")
    public void testModelUsageService() throws JsonProcessingException {
        testAutofill();
        txFeature.nextTransaction();

        ModelUsageService mus = Framework.getService(ModelUsageService.class);
        assertThat(mus).isNotNull();

        String filledUsage = mus.usage(session, AIConstants.AUTO.FILLED, "stest");
        ObjectMapper om = new ObjectMapper();
        @SuppressWarnings("unchecked")
        Map<String, Serializable> map = om.readValue(filledUsage, Map.class);
        assertThat(map).isNotEmpty();
        assertThat(map.get("hits")).isNotNull();
    }
}
