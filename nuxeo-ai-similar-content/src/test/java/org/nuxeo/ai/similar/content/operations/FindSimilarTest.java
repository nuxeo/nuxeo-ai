/*
 * (C) Copyright 2006-2021 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 * Contributors:
 *    Andrei Nechaev
 *
 */

package org.nuxeo.ai.similar.content.operations;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.FILE_CONTENT;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.similar.content.operation.FindSimilar;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.server.jaxrs.batch.BatchManager;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

@RunWith(FeaturesRunner.class)
@Features({ AutomationFeature.class })
@Deploy("org.nuxeo.ai.similar-content")
@Deploy("org.nuxeo.ai.ai-model")
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/cloud-client-test.xml")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/dedup-config-test.xml")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/disable-dedup-listener.xml")
@Deploy("org.nuxeo.ecm.automation.server")
public class FindSimilarTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(
            options().extensions(new ResponseTemplateTransformer(true)).port(5089));

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Inject
    protected BatchManager batchManager;

    @Test
    public void shouldRunOperationOnDocument() throws OperationException {
        DocumentModel fileDoc = session.createDocumentModel("/", "TestFile", "File");
        fileDoc.setPropertyValue(FILE_CONTENT, (Serializable) Blobs.createBlob("this is a blob"));
        fileDoc = session.createDocument(fileDoc);
        session.save();

        String url = "/api/v1/ai/dedup/mockTestProject/find/" + fileDoc.getId() + "/file:content" + "?distance=0";
        stubFor(WireMock.get(url).willReturn(okJson("[\"" + fileDoc.getId() + "\"]")));

        OperationContext ctx = new OperationContext(session);
        ctx.setInput(fileDoc);
        ctx.put("xpath", FILE_CONTENT);
        @SuppressWarnings("unchecked")
        List<DocumentModel> response = (List<DocumentModel>) automationService.run(ctx, FindSimilar.ID);
        assertThat(response).isNotEmpty();
    }

    @Test
    public void shouldRunOperationOnBlob() throws OperationException {
        Blob textBlob = Blobs.createBlob("this is a blob");
        DocumentModel fileDoc = session.createDocumentModel("/", "TestFile", "File");
        fileDoc.setPropertyValue(FILE_CONTENT, (Serializable) textBlob);
        fileDoc = session.createDocument(fileDoc);
        session.save();

        String url = "/api/v1/ai/dedup/mockTestProject/find?distance=0&xpath=file:content";
        stubFor(WireMock.post(url).willReturn(okJson("[\"" + fileDoc.getId() + "\"]")));

        OperationContext ctx = new OperationContext(session);
        ctx.setInput(textBlob);
        ctx.put("xpath", FILE_CONTENT);
        @SuppressWarnings("unchecked")
        List<DocumentModel> response = (List<DocumentModel>) automationService.run(ctx, FindSimilar.ID);
        assertThat(response).isNotEmpty();
    }

    @Test
    public void shouldRunOperationOnBlobFromBatchUpload() throws OperationException, IOException {
        Blob textBlob = Blobs.createBlob("this is a blob");
        DocumentModel fileDoc = session.createDocumentModel("/", "TestFile", "File");
        fileDoc.setPropertyValue(FILE_CONTENT, (Serializable) textBlob);
        fileDoc = session.createDocument(fileDoc);
        session.save();

        String url = "/api/v1/ai/dedup/mockTestProject/find?distance=0&xpath=file:content";
        stubFor(WireMock.post(url).willReturn(okJson("[\"" + fileDoc.getId() + "\"]")));

        String batchId = batchManager.initBatch();
        batchManager.addBlob(batchId, "0", textBlob, textBlob.getFilename(), textBlob.getEncoding());

        // void input
        OperationContext ctx = new OperationContext(session);
        ctx.put("batchId", batchId);
        ctx.put("fileId", "0");
        @SuppressWarnings("unchecked")
        List<DocumentModel> response = (List<DocumentModel>) automationService.run(ctx, FindSimilar.ID);
        assertThat(response).isNotEmpty();
    }

}
