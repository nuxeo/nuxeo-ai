/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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

package org.nuxeo.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nuxeo.ecm.restapi.server.jaxrs.AISearchObject.EVENT_IDS;
import static org.nuxeo.ecm.restapi.server.jaxrs.AISearchObject.MODEL_NAME;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.ecm.restapi.server.jaxrs.AISearchObject;
import org.nuxeo.ecm.restapi.test.BaseTest;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.elasticsearch.test.RepositoryElasticSearchFeature;
import org.nuxeo.jaxrs.test.CloseableClientResponse;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import freemarker.template.TemplateException;

@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class, EnrichmentTestFeature.class, PlatformFeature.class,
        RepositoryElasticSearchFeature.class })
@Deploy({ "org.nuxeo.ai.ai-core", "org.nuxeo.ai.ai-config", "org.nuxeo.ai.ai-model" })
@Deploy({ "org.nuxeo.ai.ai-config.test:test-es-contrib.xml", "org.nuxeo.elasticsearch.http.readonly" })
public class TestAISearchREST extends BaseTest {

    @Test
    public void iCanExecuteSearchOnAudit() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(MODEL_NAME, "modelName");
        queryParams.put(EVENT_IDS, "eventId");
        try (CloseableClientResponse response = getResponse(BaseTest.RequestType.GET, "aicore/search/models",
                queryParams)) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        }
    }

    @Test
    public void iCanTemplateEsJSON() throws IOException, TemplateException, URISyntaxException {
        AISearchObject aiSearchObject = new AISearchObject();
        String query = aiSearchObject.getESQuery("Model", "\"EVENT\"", null, null, null,false);
        assertThat(query).isNotEmpty();
        assertThat(query).doesNotContain("agg");
        assertThat(query).contains("\"extended.model\"");
        assertThat(query).doesNotContain("\"extended.value\"");
        assertThat(query).doesNotContain("\"from\"");
        assertThat(query).contains("\"eventId\": [\"AUTO_FILLED\",\"AUTO_CORRECTED\"]");
        query = aiSearchObject.getESQuery("Model", null, "now-90d", "now", "1", true);
        assertThat(query).isNotEmpty();
        assertThat(query).contains("agg");
        assertThat(query).contains("\"extended.model\": \"Model\"");
        assertThat(query).contains("\"extended.value\": 1");
        assertThat(query).contains("\"from\": \"now-90d\"");
        assertThat(query).contains("\"to\": \"now\"");
        assertThat(query).contains("\"eventId\": [\"AUTO_FILLED\",\"AUTO_CORRECTED\"]");
        query = aiSearchObject.getESQuery("Model", "\"AUTO_CORRECTED\",\"AUTO_FILLED\"", "now-1d","now", "0", true);
        assertThat(query).isNotEmpty();
        assertThat(query).contains("agg");
        assertThat(query).contains("\"extended.model\": \"Model\"");
        assertThat(query).contains("\"extended.value\": 0");
        assertThat(query).contains("\"from\": \"now-1d\"");
        assertThat(query).contains("\"to\": \"now\"");
        assertThat(query).contains("\"eventId\": [\"AUTO_FILLED\",\"AUTO_CORRECTED\"]");
    }
}
