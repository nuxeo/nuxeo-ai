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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.model.export.DatasetExportInterruptOperation;
import org.nuxeo.ai.model.export.DatasetExportOperation;
import org.nuxeo.ai.model.export.DatasetExportRestartOperation;
import org.nuxeo.ai.model.export.DatasetExportUpdaterOperation;
import org.nuxeo.ai.model.export.DatasetGetModelOperation;
import org.nuxeo.ai.model.export.ExportProgressOperation;
import org.nuxeo.ai.model.export.ExportProgressStatus;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.elasticsearch.test.RepositoryElasticSearchFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

@RunWith(FeaturesRunner.class)
@Features({ EnrichmentTestFeature.class, AutomationFeature.class, CoreBulkFeature.class,
        RepositoryElasticSearchFeature.class })
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
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
    public void testBadCall() {
        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();
        String inputs = "dc:title,file:content";
        params.put("query", "");
        params.put("inputs", inputs);
        params.put("outputs", "dc:description");
        params.put("split", 75);
        OperationChain chain = new OperationChain("testChain2");
        chain.add(DatasetExportOperation.ID).from(params);
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
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void shouldRunRestartOperation() throws OperationException {
        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();

        int split = 60;
        params.put("query", TEST_QUERY);

        Properties inputs = new Properties();
        inputs.put("dc:title", "txt");
        inputs.put("dc:description", "txt");
        params.put("inputProperties", inputs);

        Properties outputs = new Properties();
        outputs.put("dc:nature", "cat");
        params.put("outputProperties", outputs);

        params.put("split", split);
        OperationChain chain = new OperationChain("testChain1");
        chain.add(DatasetExportOperation.ID).from(params);
        String returned = (String) automationService.run(ctx, chain);
        assertNotNull(returned);

        txFeature.nextTransaction();

        ctx = new OperationContext(session);
        params = new HashMap<>();
        params.put("commandId", returned);
        String result = (String) automationService.run(ctx, DatasetExportRestartOperation.ID, params);
        assertNotNull(result);
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void shouldRunExportOperationWithParameters() throws OperationException {
        Map<String, Object> params = new HashMap<>();
        params.put("query", TEST_QUERY);

        Properties inputs = new Properties();
        inputs.put("dc:title", "txt");
        inputs.put("dc:description", "txt");
        params.put("inputProperties", inputs);

        Properties outputs = new Properties();
        outputs.put("dc:nature", "cat");
        params.put("outputProperties", outputs);

        params.put("split", 60);
        params.put("model_name", "Fake Name");
        params.put("model_id", "e67ee0e8-1bef-4fb7-9966-1d14081221");

        OperationContext ctx = new OperationContext(session);
        String returned = (String) automationService.run(ctx, DatasetExportOperation.ID, params);
        assertNotNull(returned);

        txFeature.nextTransaction();

        ctx = new OperationContext(session);
        params = new HashMap<>();
//        params.put("commandId", returned);

        @SuppressWarnings("unchecked")
        List<ExportProgressStatus> result = (List<ExportProgressStatus>) automationService.run(ctx,
                DatasetExportUpdaterOperation.ID, params);

        assertThat(result).isNotEmpty().hasSize(1);
        ExportProgressStatus status = result.get(0);
        assertThat(status.getId()).isEqualTo(returned);
        assertThat(status.getName()).isEqualTo("Fake Name");

        params.put("global", true);

        @SuppressWarnings("unchecked")
        List<ExportProgressStatus> globalResult = (List<ExportProgressStatus>) automationService.run(ctx,
                DatasetExportUpdaterOperation.ID, params);

        assertThat(globalResult).isNotEmpty().hasSize(1);
        status = globalResult.get(0);
        assertThat(status.getId()).isEqualTo(returned);
        assertThat(status.getName()).isEqualTo("Fake Name");

        txFeature.nextTransaction();

        Map<String, Object> statusParams = new HashMap<>();
        statusParams.put("modelId", "e67ee0e8-1bef-4fb7-9966-1d14081221");
        ExportProgressStatus progressStatus = (ExportProgressStatus) automationService.run(ctx,
                ExportProgressOperation.ID, statusParams);
        assertNotNull(progressStatus);
        assertThat(progressStatus.getId()).isEqualTo(returned);

        statusParams.put("modelId", "non_existing");
        progressStatus = (ExportProgressStatus) automationService.run(ctx, ExportProgressOperation.ID, statusParams);
        assertNull(progressStatus);
    }
}
