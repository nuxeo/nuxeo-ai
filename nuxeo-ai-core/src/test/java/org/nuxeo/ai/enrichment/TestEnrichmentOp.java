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
package org.nuxeo.ai.enrichment;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, AutomationFeature.class})
@Deploy({"org.nuxeo.ai.ai-core:OSGI-INF/enrichment-test.xml"})
public class TestEnrichmentOp {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Inject
    protected AIComponent aiComponent;

    @Test
    public void shouldCallWithParameters() throws OperationException, IOException {

        String title = "My Enriched document";
        DocumentModel testDoc = session.createDocumentModel("/", "My Doc", "File");
        testDoc.setPropertyValue("dc:title", title);
        testDoc = session.createDocument(testDoc);
        session.save();

        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();
        params.put("enrichmentName", "reverse");
        params.put("textProperties", "dc:title");
        params.put("outputVariable", "theresult");
        ctx.setInput(testDoc);
        OperationChain chain = new OperationChain("testChain1");
        chain.add(EnrichmentOp.ID).from(params);
        DocumentModel returned = (DocumentModel) automationService.run(ctx, chain);
        assertNotNull(returned);
        @SuppressWarnings("unchecked")
        List<EnrichmentMetadata> results = (List<EnrichmentMetadata>) ctx.get("theresult");
        assertEquals(1, results.size());
        EnrichmentMetadata resultMetadata = results.get(0);
        String reversed = StringUtils.reverse(title);
        assertTrue(resultMetadata.isSingleLabel());
        assertEquals(reversed, resultMetadata.getLabels().get(0).getName());
        TransientStore store = aiComponent.getTransientStoreForEnrichmentService(resultMetadata.getServiceName());
        List<Blob> rawBlobs = store.getBlobs(resultMetadata.getRawKey());
        assertNotNull(rawBlobs);
        assertEquals("There must be 1 raw blob", 1, rawBlobs.size());
        Blob blob = rawBlobs.get(0);
        assertEquals(reversed, blob.getString());
        assertNotNull(resultMetadata.getTargetDocumentRef());
        assertEquals(Arrays.asList("dc:title"), resultMetadata.getTargetDocumentProperties());
        assertTrue("reverse service sets the username so must be true", resultMetadata.isHuman());
        assertTrue("reverse service must return a single label", resultMetadata.isSingleLabel());

        try {
            params.put("enrichmentName", "I_DONT_EXIST");
            ctx.setInput(testDoc);
            OperationChain chain2 = new OperationChain("testChain2");
            chain2.add(EnrichmentOp.ID).from(params);
            automationService.run(ctx, chain2);
            fail();
        } catch (NuxeoException e) {
            assertTrue(e.getMessage().contains("Unknown enrichment service"));
        }

        try {
            params.put("enrichmentName", "e3");
            params.remove("textProperties");
            ctx.setInput(testDoc);
            OperationChain chain3 = new OperationChain("testChain3");
            chain3.add(EnrichmentOp.ID).from(params);
            automationService.run(ctx, chain3);
            fail();
        } catch (NuxeoException e) {
            assertTrue(e.getMessage().contains("You must specify either a blob or text property"));
        }
    }
}
