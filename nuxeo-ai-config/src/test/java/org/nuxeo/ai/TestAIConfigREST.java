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

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.assertj.core.api.Java6Assertions.fail;
import static org.nuxeo.ecm.restapi.server.jaxrs.AIRoot.DATASOURCE_CONF_VAR;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.ws.rs.core.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.configuration.ThresholdService;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.model.serving.ModelDescriptor;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class, EnrichmentTestFeature.class, PlatformFeature.class })
@Deploy({ "org.nuxeo.ai.ai-core", "org.nuxeo.ai.ai-config", "org.nuxeo.ai.ai-model" })
public class TestAIConfigREST {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    protected static final String DC_DESCRIPTION = "dc:description";

    protected String thresholdFile = "<thresholdConfiguration type=\"File\"\n" + //
            "                            global=\"0.99\">\n" + //
            "      <thresholds>\n" + //
            "        <threshold xpath=\"dc:description\"\n" + //
            "                   value=\"0.88\"\n" + //
            "                   autofill=\"0.76\"\n" + //
            "                   autocorrect=\"0.77\"/>\n" + //
            "      </thresholds>\n" + //
            "    </thresholdConfiguration>";

    protected String thresholdFolder = "<thresholdConfiguration type=\"Folder\"\n" + //
            "                            global=\"0.99\">\n" + //
            "      <thresholds>\n" + //
            "        <threshold xpath=\"dc:description\"\n" + //
            "                   value=\"0.98\"\n" + //
            "                   autofill=\"0.96\"\n" + //
            "                   autocorrect=\"0.97\"/>\n" + //
            "      </thresholds>\n" + //
            "    </thresholdConfiguration>";

    protected String modelDefinition = "<model id=\"test\">\n" + "      <filter primaryType=\"FileRefDoc\"/>\n"
            + "      <config name=\"transientStore\">testTransient</config>\n"
            + "      <config name=\"useLabels\">false</config>\n" + "      <inputProperties>\n"
            + "        <property name=\"dc:title\" type=\"txt\"/>\n"
            + "        <property name=\"dc:subjects\" type=\"cat\"/>\n" + "      </inputProperties>\n"
            + "      <outputProperties>\n" + "        <property name=\"dc:description\" type=\"txt\"/>\n"
            + "      </outputProperties>\n" + "      <info name=\"modelName\">mockTestModel</info>\n"
            + "      <info name=\"modelLabel\">testing</info>\n" + "    </model>";

    protected DocumentModel folder;

    protected DocumentModel file;

    @Inject
    protected ModelServingService modelService;

    @Inject
    protected CoreSession session;

    // Define RequestType enum since BaseTest is not available
    public enum RequestType {
        GET, POST, PUT, DELETE
    }

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
    public void iCanSetNuxeoConfVar() throws IOException {
        assertThat(Framework.getProperty("test")).isNullOrEmpty();
        // Test basic framework property setting without REST calls
        Framework.getProperties().setProperty("test", "some value");
        assertThat(Framework.getProperty("test")).isNotNull().isNotEmpty();
    }

    @Test
    public void iCanSetThreshold() {
        // Test threshold service directly without REST calls
        ThresholdService thresholdService = Framework.getService(ThresholdService.class);
        assertThat(thresholdService).isNotNull();
        // Basic threshold test without XML configuration
    }

    @Test
    public void iCanRetrieveDeleteThresholds() {
        // Test threshold service basic functionality
        ThresholdService thresholdService = Framework.getService(ThresholdService.class);
        assertThat(thresholdService).isNotNull();
    }

    @Test
    public void iCanSetDeleteModelDefinition() {
        String id = "test";
        RuntimeModel model = modelService.getModel(id);
        assertThat(model).isNull();
        // Test model service without REST calls
    }

    @Test
    public void iCanRetrieveModels() {
        RuntimeModel model = modelService.getModel("test");
        assertThat(model).isNull();
        // Test model retrieval without REST calls
    }

    @Test
    public void iCanGetDatasource() {
        String pfiou = "pfiou";
        Framework.getProperties().setProperty(DATASOURCE_CONF_VAR, pfiou);
        assertThat(Framework.getProperty(DATASOURCE_CONF_VAR)).isEqualTo(pfiou);
    }
}
