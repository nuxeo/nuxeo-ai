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
package org.nuxeo.ai.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_INPUTS;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_JOBID;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_OUTPUTS;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_QUERY;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_SPLIT;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.IMAGE_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.NAME_PROP;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TYPE_PROP;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.model.export.DatasetExportInterruptOperation;
import org.nuxeo.ai.model.export.DatasetExportOperation;
import org.nuxeo.ai.model.export.DatasetExportRestartOperation;
import org.nuxeo.ai.model.export.DatasetGetModelOperation;
import org.nuxeo.ai.model.export.DatasetUploadOperation;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.elasticsearch.test.RepositoryElasticSearchFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

@RunWith(FeaturesRunner.class)
@Features({AutomationFeature.class, CoreBulkFeature.class, RepositoryElasticSearchFeature.class})
@Deploy("org.nuxeo.ai.ai-core")
@Deploy("org.nuxeo.ai.ai-core:OSGI-INF/recordwriter-test.xml")
@Deploy("org.nuxeo.ai.ai-model")
@Deploy("org.nuxeo.elasticsearch.core.test:elasticsearch-test-contrib.xml")
public class TestDatasetOperation {

    public static final String TEST_QUERY = "SELECT * from document WHERE dc:title IS NOT NULL";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(5089);

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Inject
    protected TransactionalFeature txFeature;

    @Test
    public void shouldCallWithParameters() throws OperationException {

        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();

        int split = 60;
        params.put("query", TEST_QUERY);
        params.put("inputs", "dc:title,dc:description");
        params.put("outputs", "dc:nature");
        params.put("split", split);
        OperationChain chain = new OperationChain("testChain1");
        chain.add(DatasetExportOperation.ID).from(params);
        String returned = (String) automationService.run(ctx, chain);
        assertNotNull(returned);

        DocumentModel doc = getCorpusDoc(returned);
        assertEquals(TEST_QUERY, doc.getPropertyValue(CORPUS_QUERY));
        assertEquals(Long.valueOf(split), doc.getPropertyValue(CORPUS_SPLIT));
    }

    protected DocumentModel getCorpusDoc(String returned) {
        txFeature.nextTransaction();
        List<DocumentModel> docs = session.query(String.format("SELECT * FROM %s WHERE %s = '%s'",
                                                               CORPUS_TYPE,
                                                               CORPUS_JOBID,
                                                               returned));
        assertEquals("A corpus document must be created.", 1, docs.size());
        return docs.get(0);
    }

    @Test
    public void testBadCall() {

        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();
        String inputs = "dc:title,file:content";
        String unknownProp = "dc:on_no_you_dont";
        params.put("query", "");
        params.put("inputs", inputs);
        params.put("outputs", "dc:description");
        params.put("split", 75);
        OperationChain chain = new OperationChain("testChain2");
        chain.add(DatasetExportOperation.ID).from(params);
        String returned = null;
        try {
            automationService.run(ctx, chain);
            fail();
        } catch (OperationException e) {
            assertEquals(IllegalArgumentException.class, e.getCause().getClass());
        }

        try {
            OperationChain chain3 = new OperationChain("testChain3");
            params.put("query", "SELECT * FROM DOCUMENT");
            params.remove("split");
            chain3.add(DatasetExportOperation.ID).from(params);
            automationService.run(ctx, chain3);
            fail();
        } catch (OperationException e) {
            assertEquals(IllegalArgumentException.class, e.getCause().getClass());
        }

        try {
            OperationChain chainBadSplit = new OperationChain("testChainbad");
            params.put("query", TEST_QUERY);
            params.put("split", 600);
            chainBadSplit.add(DatasetExportOperation.ID).from(params);
            automationService.run(ctx, chainBadSplit);
            fail();
        } catch (OperationException e) {
            assertEquals(IllegalArgumentException.class, e.getCause().getClass());
        }
    }

    @Test
    public void testBlob() throws OperationException {

        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();
        params.put("query", TEST_QUERY);
        params.put("inputs", "dc:title,file:content");
        params.put("outputs", "dc:nature,dc:created");
        OperationChain chain = new OperationChain("testChain1");
        chain.add(DatasetExportOperation.ID).from(params);
        String returned = (String) automationService.run(ctx, chain);
        assertNotNull(returned);

        DocumentModel doc = getCorpusDoc(returned);
        assertEquals(TEST_QUERY, doc.getPropertyValue(CORPUS_QUERY));

        @SuppressWarnings("unchecked")
        List<Map> inputs = (List<Map>) doc.getPropertyValue(CORPUS_INPUTS);
        assertEquals(2, inputs.size());
        assertTrue(inputs.stream().anyMatch(
                p -> "file:content".equals(p.get(NAME_PROP)) && IMAGE_TYPE.equals(p.get(TYPE_PROP))));

        @SuppressWarnings("unchecked")
        List<Map> outputs = (List<Map>) doc.getPropertyValue(CORPUS_OUTPUTS);
        assertEquals(2, outputs.size());

        ctx = new OperationContext(session);
        ctx.setInput(doc);
        chain = new OperationChain("uploadAgainChain1");
        chain.add(DatasetUploadOperation.ID);
        automationService.run(ctx, chain);

        params.clear();
        params.put("document", doc);
        ctx = new OperationContext(session);
        chain = new OperationChain("uploadAgainChain2");
        chain.add(DatasetUploadOperation.ID).from(params);
        automationService.run(ctx, chain);
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void shouldRetrieveAIModels() throws OperationException {
        OperationContext ctx = new OperationContext(session);
        JSONBlob result = (JSONBlob) automationService.run(ctx, DatasetGetModelOperation.ID);
        assertNotNull(result);
    }

    @Test
    public void shouldRunInterruptOperation() throws OperationException {
        OperationContext ctx = new OperationContext(session);
        HashMap<String, Serializable> params = new HashMap<>();
        params.put("commandId", "fakeOne");
        boolean result = (boolean) automationService.run(ctx, DatasetExportInterruptOperation.ID, params);
        assertTrue(result);
    }

    @Test
    public void shouldRunRestartOperation() throws OperationException {
        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();

        int split = 60;
        params.put("query", TEST_QUERY);
        params.put("inputs", "dc:title,dc:description");
        params.put("outputs", "dc:nature");
        params.put("split", split);
        OperationChain chain = new OperationChain("testChain1");
        chain.add(DatasetExportOperation.ID).from(params);
        String returned = (String) automationService.run(ctx, chain);
        assertNotNull(returned);

        ctx = new OperationContext(session);
        params = new HashMap<>();
        params.put("commandId", returned);
        String result = (String) automationService.run(ctx, DatasetExportRestartOperation.ID, params);
        assertNotNull(result);
    }
}
