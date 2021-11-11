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
 *    Nuxeo
 *
 */

package org.nuxeo.ai.similar.content.operations;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.FILE_CONTENT;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.core.Response;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.similar.content.operation.DedupDeleteIndexOperation;
import org.nuxeo.ai.similar.content.operation.DedupIndexOperation;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.sun.jersey.core.spi.factory.ResponseImpl;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, AutomationFeature.class })
@Deploy("org.nuxeo.ai.similar-content")
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
@Deploy("org.nuxeo.ai.ai-model")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/cloud-client-datasource-test.xml")
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
public class DeduplicationOperationsTest {

    public static final String XPATH = "file:content";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(
            options().extensions(new ResponseTemplateTransformer(true)).port(5089));

    @Inject
    protected AutomationService automationService;

    @Inject
    protected CoreSession session;

    @Test
    public void iCanUseIndexOperation() throws OperationException {
        OperationContext ctx = new OperationContext(session);

        Blob textBlob = Blobs.createBlob("this is a blob");
        DocumentModel fileDoc = session.createDocumentModel("/", "TestFile", "File");
        fileDoc.setPropertyValue(FILE_CONTENT, (Serializable) textBlob);
        fileDoc = session.createDocument(fileDoc);
        session.save();

        String url = "/api/v1/ai/dedup/mockTestProject/index/" + fileDoc.getId() + "/" + FILE_CONTENT;
        stubFor(WireMock.post(url).willReturn(ok()));

        ctx.setInput(fileDoc);
        Map<String, Object> params = new HashMap<>();
        params.put("xpath", XPATH);
        ResponseImpl response = (ResponseImpl) automationService.run(ctx, DedupIndexOperation.ID, params);
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    }

    @Test
    public void iCanUseDeleteIndexOperation() throws OperationException {
        DocumentModel fileDoc = session.createDocumentModel("/", "TestFile", "File");
        fileDoc = session.createDocument(fileDoc);
        session.save();

        String url = "/api/v1/ai/dedup/mockTestProject/index/" + fileDoc.getId() + "?xpath=" + FILE_CONTENT;
        stubFor(WireMock.delete(url).willReturn(ok()));

        OperationContext ctx = new OperationContext(session);
        ctx.setInput(fileDoc);
        Map<String, Object> params = new HashMap<>();
        params.put("xpath", XPATH);
        ResponseImpl response = (ResponseImpl) automationService.run(ctx, DedupDeleteIndexOperation.ID, params);
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    }
}
