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
package org.nuxeo.ai.similar.content.listeners;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.FILE_CONTENT;
import static org.nuxeo.ai.similar.content.DedupConstants.CONF_LISTENER_ENABLE;

import java.io.Serializable;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.similar.content.mock.ResolveDuplicatesListener;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, AutomationFeature.class })
@Deploy("org.nuxeo.ai.similar-content")
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
@Deploy("org.nuxeo.ai.ai-model")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/cloud-client-datasource-test.xml")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/mock-listener-contrib.xml")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/override-dedup-default-config.xml")
public class DocumentIndexedListenerTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(
            options().extensions(new ResponseTemplateTransformer(true)).port(5089));

    @Inject
    protected CoreSession session;

    @Inject
    protected TransactionalFeature txf;

    @Inject
    protected EventService es;

    @Before
    public void init(){
        Framework.getProperties().put(CONF_LISTENER_ENABLE, "true");
    }

    @Test
    public void shouldProvideListOfSimilarDocs() {
        DocumentModel fileDoc = session.createDocumentModel("/", "TestFile", "File");
        fileDoc.setPropertyValue(FILE_CONTENT, (Serializable) Blobs.createBlob("this is a blob"));
        fileDoc = session.createDocument(fileDoc);

        String indexURL = "/api/v1/ai/dedup/mockTestProject/index/" + fileDoc.getId() + "?xpath=" + FILE_CONTENT;
        stubFor(WireMock.post(indexURL).willReturn(ok()));

        String deleteURL = "/api/v1/ai/dedup/mockTestProject/index/" + fileDoc.getId();
        stubFor(WireMock.delete(deleteURL).willReturn(ok()));

        String findURL =
                "/api/v1/ai/dedup/mockTestProject/find/" + fileDoc.getId() + "/" + FILE_CONTENT + "?distance=0";
        stubFor(WireMock.get(findURL).willReturn(okJson("[\"" + fileDoc.getId() + "\", \"" + fileDoc.getId() + "\"]")));

        txf.nextTransaction();

        es.waitForAsyncCompletion();

        assertThat(ResolveDuplicatesListener.similarIds).isNotEmpty();
        assertThat(ResolveDuplicatesListener.docRef.get()).isNotNull();
    }
}
