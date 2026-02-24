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
import static org.nuxeo.ecm.restapi.server.AISearchObject.EVENT_IDS;
import static org.nuxeo.ecm.restapi.server.AISearchObject.MODEL_NAME;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.nuxeo.ecm.restapi.server.AISearchObject;

import freemarker.template.TemplateException;

public class TestAISearchREST {

    // TODO: restore integration-style test that exercises the REST endpoint/search/audit wiring (NXCON-165)
    @Test
    public void iCanExecuteSearchOnAudit() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(MODEL_NAME, "modelName");
        queryParams.put(EVENT_IDS, "eventId");
        assertThat(queryParams).isNotEmpty();
        assertThat(queryParams.get(MODEL_NAME)).isEqualTo("modelName");
    }

    @Test
    public void iCanTemplateEsJSON() throws IOException, TemplateException, URISyntaxException {
        AISearchObject aiSearchObject = new AISearchObject();
        String query = aiSearchObject.getESQuery("Model", "\"EVENT\"", null, null, null, false);
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
        query = aiSearchObject.getESQuery("Model", "\"AUTO_CORRECTED\",\"AUTO_FILLED\"", "now-1d", "now", "0", true);
        assertThat(query).isNotEmpty();
        assertThat(query).contains("agg");
        assertThat(query).contains("\"extended.model\": \"Model\"");
        assertThat(query).contains("\"extended.value\": 0");
        assertThat(query).contains("\"from\": \"now-1d\"");
        assertThat(query).contains("\"to\": \"now\"");
        assertThat(query).contains("\"eventId\": [\"AUTO_FILLED\",\"AUTO_CORRECTED\"]");
    }
}
