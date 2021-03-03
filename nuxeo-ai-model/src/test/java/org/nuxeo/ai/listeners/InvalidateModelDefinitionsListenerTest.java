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

import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.model.serving.ModelDescriptor;
import org.nuxeo.ai.model.serving.ModelServingService;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.EventBundleImpl;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.ecm.core.event.impl.EventImpl;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.HotDeployer;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, AutomationFeature.class })
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
@Deploy("org.nuxeo.ai.ai-core" )
@Deploy("org.nuxeo.ai.ai-model")
@Deploy("org.nuxeo.ecm.core.cache")
@Deploy("org.nuxeo.ecm.core.event")
@Deploy("org.nuxeo.runtime.pubsub")
@Deploy("org.nuxeo.ai.ai-model:OSGI-INF/model-serving-update-test.xml")
public class InvalidateModelDefinitionsListenerTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(
            options().extensions(new ResponseTemplateTransformer(true)).port(5089));

    @Inject
    protected CoreSession session;

    @Inject
    protected ModelServingService mss;

    @Inject
    protected EventService es;

    @Inject
    protected TransactionalFeature txf;

    @Inject
    protected HotDeployer hotDeployer;

    @Test
    public void shouldLunchExportWithNoExceptions() throws Exception {
        Collection<ModelDescriptor> models = mss.listModels();
        List<String> ids = models.stream().map(ModelDescriptor::getId).collect(Collectors.toList());
        assertThat(mss.listModels()).hasSize(2);
        assertThat(ids).doesNotContain("Components", "tags");

        hotDeployer.deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml");
        EventBundle bundle = new EventBundleImpl();
        EventContextImpl ctx = new EventContextImpl(session, session.getPrincipal());
        bundle.push(new EventImpl(InvalidateModelDefinitionsListener.EVENT_NAME, ctx));

        es.fireEventBundle(bundle);

        txf.nextTransaction();
        es.waitForAsyncCompletion();

        models = mss.listModels();
        assertThat(models).hasSize(2);
        ids = models.stream().map(ModelDescriptor::getId).collect(Collectors.toList());
        assertThat(ids).contains("Components", "tags");
    }
}
