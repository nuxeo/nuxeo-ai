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
import static org.nuxeo.ai.pipes.functions.PropertyUtils.FILE_CONTENT;

import java.io.Serializable;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
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

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, AutomationFeature.class })
@Deploy("org.nuxeo.ai.similar-content")
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
@Deploy("org.nuxeo.ai.ai-model")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/cloud-client-datasource-test.xml")
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
public class DeduplicationListenersTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(
            options().extensions(new ResponseTemplateTransformer(true)).port(5089));

    @Inject
    protected CoreSession session;

    @Test
    public void shouldManageIndexFromListener() {
        Blob textBlob = Blobs.createBlob("this is a blob");
        DocumentModel fileDoc = session.createDocumentModel("/", "TestFile", "File");
        fileDoc.setPropertyValue(FILE_CONTENT, (Serializable) textBlob);
        fileDoc = session.createDocument(fileDoc);

        // When the doc blob is deleted
        String url = "/api/v1/ai/dedup/mockTestProject/index/" + fileDoc.getId() + "?xpath=" + FILE_CONTENT;
        stubFor(WireMock.delete(url).willReturn(ok()));

        // When the doc is deleted
        url = "/api/v1/ai/dedup/mockTestProject/index/" + fileDoc.getId();
        stubFor(WireMock.delete(url).willReturn(ok()));

        textBlob = Blobs.createBlob("this is another blob");
        fileDoc.setPropertyValue(FILE_CONTENT, (Serializable) textBlob);
        fileDoc = session.saveDocument(fileDoc);

        fileDoc.setPropertyValue(FILE_CONTENT, null);
        fileDoc = session.saveDocument(fileDoc);

        session.removeDocument(new IdRef(fileDoc.getId()));
    }

}
