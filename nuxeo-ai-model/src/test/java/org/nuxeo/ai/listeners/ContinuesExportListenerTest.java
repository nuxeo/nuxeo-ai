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

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.impl.EventBundleImpl;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.ecm.core.event.impl.EventImpl;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.elasticsearch.test.RepositoryElasticSearchFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, AutomationFeature.class, PlatformFeature.class, CoreBulkFeature.class, RepositoryElasticSearchFeature.class})
@Deploy("org.nuxeo.ai.ai-model")
@Deploy("org.nuxeo.elasticsearch.core.test:elasticsearch-test-contrib.xml")
public class ContinuesExportListenerTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().extensions(new ResponseTemplateTransformer(true))
            .port(5089));

    @Inject
    protected CoreSession session;

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void shouldLunchExportWithNoExceptions() {
        EventBundle bundle = new EventBundleImpl();
        EventContextImpl ctx = new EventContextImpl(session, session.getPrincipal());
        bundle.push(new EventImpl("startContinuesExport", ctx));

        ContinuesExportListener listener = new ContinuesExportListener();
        listener.handleEvent(bundle);
    }
}
