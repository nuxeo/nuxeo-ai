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

package org.nuxeo.ai.similar.content.pipelines;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.nuxeo.ai.sdk.rest.Common.Headers.SCROLL_ID_HEADER;
import static org.nuxeo.ai.similar.content.DedupConstants.CONF_LISTENER_ENABLE;
import static org.nuxeo.ai.similar.content.pipelines.DeduplicationScrollerComputation.SCROLLER_COMPUTATION_NAME;
import static org.nuxeo.ai.similar.content.pipelines.DuplicateResolverComputation.RESOLVER_COMPUTE_NAME;
import static org.nuxeo.ai.similar.content.pipelines.DuplicationPipeline.PIPELINE_NAME;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.similar.content.operation.ProcessDuplicates;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.StreamService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;

@RunWith(FeaturesRunner.class)
@Features({ AutomationFeature.class, CoreBulkFeature.class })
@Deploy("org.nuxeo.ai.similar-content")
@Deploy("org.nuxeo.ai.ai-model")
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/cloud-client-test.xml")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/dedup-config-test.xml")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/operations-test-contrib.xml")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/disable-dedup-listener.xml")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/activate-dedup-stream.xml")
@RepositoryConfig(cleanup = Granularity.METHOD)
public class DeduplicationPipelineTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(
            options().extensions(new ResponseTemplateTransformer(true)).port(5089));

    @Inject
    protected AutomationService as;

    @Inject
    protected CoreSession session;

    @Inject
    protected TransactionalFeature txf;

    @Before
    public void init() {
        Framework.getProperties().put(CONF_LISTENER_ENABLE, "true");
    }

    @Test
    public void shouldScrollThroughTuples() throws OperationException, InterruptedException {
        DocumentModel base = session.createDocumentModel("/", "base_doc", "File");
        base = session.createDocument(base);

        List<String> ids = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            DocumentModel doc = session.createDocumentModel("/", "doc" + i, "File");
            doc = session.createDocument(doc);
            ids.add(doc.getId());
        }

        txf.nextTransaction();

        // 5 documents were created
        assertThat(ids.stream().allMatch(id -> session.exists(new IdRef(id)))).isTrue();

        StringBuilder response = new StringBuilder("{\n" //
                + "    \"scrollId\": \"testScrollId\",\n"//
                + "    \"result\": [\n"//
                + "        {\n" //
                + "            \"documentId\": \"" + base.getId() + "\",\n"//
                + "            \"xpath\": \"file:content\",\n" //
                + "            \"similarDocuments\": [\n");//

        for (String id : ids) {
            response.append("{\n" //
                    + "\"").append(id).append("\": \"file:content\"\n" //
            ).append("},\n");//
        }

        response.deleteCharAt(response.length() - 2); // remove comma
        response.append("]\n" //
                + "        }\n" //
                + "    ]\n" //
                + "}");

        stubFor(WireMock.get("/api/v1/ai/dedup/mockTestProject/similars")
                        .withHeader(SCROLL_ID_HEADER, StringValuePattern.ABSENT)
                        .willReturn(okJson(response.toString())));
        // Second stub is intended for mimicking the end of the scroller
        String emptyResponse = "{\n" //
                + "    \"scrollId\": \"testScrollId\",\n"//
                + "    \"result\": [\n"//
                + "    ]\n" //
                + "}";
        stubFor(WireMock.get("/api/v1/ai/dedup/mockTestProject/similars")
                        .withHeader(SCROLL_ID_HEADER, new EqualToPattern("testScrollId", false))
                        .willReturn(okJson(emptyResponse)));

        OperationContext ctx = new OperationContext(session);
        as.run(ctx, ProcessDuplicates.ID);

        txf.nextTransaction();

        LogManager manager = Framework.getService(StreamService.class).getLogManager();
        awaitPipeline(manager, SCROLLER_COMPUTATION_NAME, Duration.ofSeconds(10));
        txf.nextTransaction();
        awaitPipeline(manager, RESOLVER_COMPUTE_NAME, Duration.ofSeconds(10));

        // base document remained
        assertThat(session.exists(base.getRef())).isTrue();
        // 5 documents previously created now removed by the dedup pipeline
        assertThat(ids.stream().noneMatch(id -> session.exists(new IdRef(id)))).isTrue();
    }

    private void awaitPipeline(LogManager manager, String computation, Duration duration) throws InterruptedException {
        long deadline = System.currentTimeMillis() + duration.toMillis();
        while (manager.getLag(PIPELINE_NAME, computation).lag() > 0) {
            if (System.currentTimeMillis() > deadline) {
                break;
            }

            Thread.sleep(100);
        }
    }
}
