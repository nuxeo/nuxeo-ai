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
package org.nuxeo.ai;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import javax.ws.rs.core.Response.Status;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.restapi.test.BaseTest;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.jaxrs.test.CloseableClientResponse;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.ServletContainerFeature;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class, ServletContainerFeature.class })
@Deploy("org.nuxeo.ai.ai-model")
@Deploy("org.nuxeo.ai.ai-jaxrs")
public class TestRESTAPI extends BaseTest {

    public static final String AI_CLOUD_PATH = "ai/cloud/path";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(5084);

    @Test
    public void testNoClient() {
        try (CloseableClientResponse response = getResponse(RequestType.GET, AI_CLOUD_PATH + "/models")) {
            assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        }
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-jaxrs:OSGI-INF/cloud-client-test.xml")
    public void testGetByPath() throws IOException {
        try (CloseableClientResponse response = getResponse(RequestType.GET, "ai/")) {
            // Method not allowed
            assertEquals(405, response.getStatus());
        }

        try (CloseableClientResponse response = getResponse(RequestType.GET,
                AI_CLOUD_PATH + "/models?enrichers.document=children")) {
            assertEquals(Status.OK.getStatusCode(), response.getStatus());
            JsonNode node = mapper.readTree(response.getEntityInputStream());
            assertEquals("document", node.get("entity-type").asText());
            assertEquals(6, node.get("contextParameters").get("children").get("entries").size());
        }

        try (CloseableClientResponse response = getResponse(RequestType.GET, AI_CLOUD_PATH + "/models/@children")) {
            assertEquals(Status.OK.getStatusCode(), response.getStatus());
            JsonNode node = mapper.readTree(response.getEntityInputStream());
            assertEquals("documents", node.get("entity-type").asText());
            assertEquals(6, node.get("resultsCount").asInt());
        }

        try (CloseableClientResponse response = getResponse(RequestType.GET, AI_CLOUD_PATH + "/models/pet_model")) {
            assertEquals(Status.OK.getStatusCode(), response.getStatus());
            JsonNode node = mapper.readTree(response.getEntityInputStream());
            assertEquals("document", node.get("entity-type").asText());
            assertEquals("trained", node.get("state").asText());
        }

        try (CloseableClientResponse response = getResponse(RequestType.GET, AI_CLOUD_PATH + "/datasets/@children")) {
            assertEquals(Status.OK.getStatusCode(), response.getStatus());
            JsonNode node = mapper.readTree(response.getEntityInputStream());
            assertEquals("documents", node.get("entity-type").asText());
            assertEquals(8, node.get("resultsCount").asInt());
        }
    }

}
