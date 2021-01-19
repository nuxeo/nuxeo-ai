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

import javax.ws.rs.core.Response;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.ecm.restapi.test.BaseTest;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.elasticsearch.test.RepositoryElasticSearchFeature;
import org.nuxeo.jaxrs.test.CloseableClientResponse;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class, EnrichmentTestFeature.class, PlatformFeature.class,
        RepositoryElasticSearchFeature.class })
@Deploy({ "org.nuxeo.ai.ai-core", "org.nuxeo.ai.ai-config", "org.nuxeo.ai.ai-model" })
@Deploy({ "org.nuxeo.ai.ai-config.test:test-es-contrib.xml", "org.nuxeo.elasticsearch.http.readonly" })
public class TestAISearchREST extends BaseTest {

    protected String payload = "{\"query\": {}}";

    @Test
    public void iCanExecuteSearchOnAudit() {
        try (CloseableClientResponse response = getResponse(BaseTest.RequestType.POST, "aicore/search/audit",
                payload)) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        }
    }
}
