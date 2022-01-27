/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.listeners;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.nuxeo.ai.listeners.ContinuousExportListener.FORCE_EXPORT;
import static org.nuxeo.ai.listeners.ContinuousExportListener.START_CONTINUOUS_EXPORT;

import java.util.List;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.model.export.DatasetExportService;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.EventBundleImpl;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.ecm.core.event.impl.EventImpl;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.elasticsearch.test.RepositoryElasticSearchFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

@RunWith(FeaturesRunner.class)
@Features({ EnrichmentTestFeature.class, AutomationFeature.class, PlatformFeature.class, CoreBulkFeature.class,
        RepositoryElasticSearchFeature.class })
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
@Deploy("org.nuxeo.ai.ai-model")
@Deploy("org.nuxeo.elasticsearch.core.test:elasticsearch-test-contrib.xml")
@Deploy({ "org.nuxeo.ai.ai-model:OSGI-INF/disable-ai-listeners.xml" })
public class ContinuousExportListenerTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(
            options().extensions(new ResponseTemplateTransformer(true)).port(5089));

    @Inject
    protected CoreSession session;

    @Inject
    protected EventService es;

    @Inject
    protected DatasetExportService des;

    @Inject
    protected TransactionalFeature txf;

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void shouldLunchExportWithNoExceptions() {
        for (int i = 0; i < 15; i++) {
            DocumentModel file = session.createDocumentModel("/", "TestDocument" + i, "File");
            file.setPropertyValue("dc:title", "Test Doc " + 1);
            session.createDocument(file);
        }
        txf.nextTransaction();

        List<BulkStatus> statuses = des.getStatuses();
        assertThat(statuses).hasSize(0);

        EventBundle bundle = new EventBundleImpl();
        EventContextImpl ctx = new EventContextImpl(session, session.getPrincipal());
        ctx.setProperty(FORCE_EXPORT, true);
        bundle.push(new EventImpl(START_CONTINUOUS_EXPORT, ctx));
        es.fireEventBundle(bundle);
        es.waitForAsyncCompletion();

        txf.nextTransaction();

        statuses = des.getStatuses();
        // 4 models were returned where 3 documents represent 1 live and 2 versions of it -> produced 2 exports;
        // look at nuxeo-ai-model/src/test/resources/mappings/nuxeo_get_ai_models.json
        assertThat(statuses).hasSize(2);
    }
}
