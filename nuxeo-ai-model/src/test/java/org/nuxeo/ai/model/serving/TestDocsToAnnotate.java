/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo
 */
package org.nuxeo.ai.model.serving;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.ecm.restapi.test.BaseTest;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, AutomationFeature.class })
@Deploy({ "org.nuxeo.ai.ai-core", "org.nuxeo.ai.ai-model", "org.nuxeo.ai.ai-model:OSGI-INF/override-file-doctype.xml",
        "org.nuxeo.ecm.platform.url.api", "org.nuxeo.ecm.platform.url.core", "org.nuxeo.ecm.platform.types.api",
        "org.nuxeo.ecm.platform.types.core" })
public class TestDocsToAnnotate extends BaseTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(5089);

    @Inject
    protected CoreSession session;

    protected List<String> uids;

    @Inject
    protected AutomationService automationService;

    @Before
    public void init() throws IOException {
        Blob file = new FileBlob(this.getClass().getResourceAsStream("/files/pink.jpg"));
        DocumentModel reference = session.createDocumentModel("/", "Reference", "File");
        reference = session.createDocument(reference);
        session.save();
        uids = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            DocumentModel document = session.createDocumentModel("/", "Example " + i, "ExtraFile");
            document.setPropertyValue("dc:title", "Example " + i);
            document.setPropertyValue("dc:description", "Description " + i);
            document.setPropertyValue("dc:contributors", new String[] { "system", "Administrator" });
            document.setPropertyValue("dc:subjects", new String[] { "art/architecture", "art/danse" });
            document.setPropertyValue("file:content", (Serializable) file);
            document.setPropertyValue("extrafile:docprop", reference.getId());
            uids.add(session.createDocument(document).getId());
        }
        session.save();
    }

    @Test
    public void iCanGetDocsToAnnotate() throws IOException, OperationException, JsonProcessingException {
        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();
        params.put("uids", uids);
        List<String> inputs = new ArrayList<String>() {
            {
                add("dc:description");
                add("dc:contributors");
                add("dc:subjects");
                add("file:content");
                add("extrafile:docprop");
            }
        };
        params.put("inputs", inputs);
        List<String> outputs = new ArrayList<String>() {
            {
                add("dc:title");
                add("dc:nature");
                add("file:content");
            }
        };
        params.put("outputs", outputs);
        JSONBlob results = (JSONBlob) automationService.run(ctx, FetchDocsToAnnotate.ID, params);
        JsonNode jsonNode = mapper.readTree(results.getString());
        assertThat(jsonNode.isArray()).isTrue();
        assertThat(jsonNode.size()).isEqualTo(20);
        assertThat(jsonNode.get(0).get("docId")).isNotNull();
        JsonNode inputResults = jsonNode.get(0).get("inputs");
        assertThat(inputResults.isArray()).isTrue();
        assertThat(inputResults.get(0).get("type")).isNotNull();
        assertThat(inputResults.get(0).get("name")).isNotNull();
        assertThat(inputResults.get(0).get("value")).isNotNull();
        assertThat(inputResults.get(2).get("value").isArray()).isTrue();
        assertThat(inputResults.get(2).get("value")).isNotEmpty();
        assertThat(inputResults.get(3).get("value").has("data")).isTrue();
        JsonNode outputResults = jsonNode.get(0).get("outputs");
        assertThat(inputResults.isArray()).isTrue();
        assertThat(inputResults.get(0).get("type")).isNotNull();
    }
}
