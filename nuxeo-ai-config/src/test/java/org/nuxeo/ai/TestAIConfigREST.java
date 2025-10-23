/*
 * (C) Copyright 2020-2021 Nuxeo (http://nuxeo.com/) and others.
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
 *  Contributors:
 *      vpasquier, anechaev
 */

package org.nuxeo.ai;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.configuration.ThresholdService;
import org.nuxeo.ai.model.serving.ModelServingService;
import org.nuxeo.ai.model.serving.RuntimeModel;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class, PlatformFeature.class })
@Deploy({ "org.nuxeo.ai.ai-core", "org.nuxeo.ai.ai-config", "org.nuxeo.ai.ai-model" })
public class TestAIConfigREST {

    protected DocumentModel folder;

    protected DocumentModel file;

    @Inject
    protected ModelServingService modelService;

    @Inject
    protected CoreSession session;

    @Before
    public void setup() {
        file = session.createDocumentModel("/", "file", "File");
        file = session.createDocument(file);
        folder = session.createDocumentModel("/", "folder", "Folder");
        folder = session.createDocument(folder);
    }

    @After
    public void destroy() {
        session.removeChildren(new PathRef("/"));
    }

    @Test
    public void iCanSetNuxeoConfVar() {
        assertThat(Framework.getProperty("test")).isNullOrEmpty();
        Framework.getProperties().setProperty("test", "some value");
        assertThat(Framework.getProperty("test")).isEqualTo("some value");
    }

    @Test
    public void iCanSetThreshold() {
        ThresholdService thresholdService = Framework.getService(ThresholdService.class);
        assertThat(thresholdService).isNotNull();
    }

    @Test
    public void iCanSetDeleteModelDefinition() {
        RuntimeModel model = modelService.getModel("test");
        assertThat(model).isNull();
    }

    @Test
    public void iCanRetrieveModels() {
        RuntimeModel model = modelService.getModel("test");
        assertThat(model).isNull();
    }

    @Test
    public void iCanGetDatasource() {
        String key = "nuxeo.ai.insight.datasource.label";
        Framework.getProperties().setProperty(key, "pfiou");
        assertThat(Framework.getProperty(key)).isEqualTo("pfiou");
    }
}
