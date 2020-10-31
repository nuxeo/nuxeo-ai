/*
 *
 * (C) Copyright 2020 Nuxeo SA (http://nuxeo.com/).
 * This is unpublished proprietary source code of Nuxeo SA. All rights reserved.
 * Notice of copyright on this source code does not indicate publication. *
 *
 *
 * Contributors:
 *     Nuxeo
 */

package org.nuxeo.ai;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.configuration.ThresholdService;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.model.serving.ModelServingService;
import org.nuxeo.ai.model.serving.RuntimeModel;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.ecm.restapi.test.BaseTest;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.jaxrs.test.CloseableClientResponse;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;
import com.sun.jersey.core.util.MultivaluedMapImpl;

@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class, EnrichmentTestFeature.class, PlatformFeature.class })
@Deploy({ "org.nuxeo.ai.ai-core", "org.nuxeo.ai.ai-config", "org.nuxeo.ai.ai-model" })
public class TestAIConfigREST extends BaseTest {

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
        Map<String, String> confVar = new HashMap<>();
        confVar.put("test", "some value");
        String body = mapper.writeValueAsString(confVar);
        try (CloseableClientResponse response = getResponse(BaseTest.RequestType.POST, "aicore/config", body)) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
            assertThat(Framework.getProperty("test")).isNotNull().isNotEmpty();
        }
    }

    @Test
    public void iCanSetThreshold() {
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.putSingle("docType", "File");
        try (CloseableClientResponse response = getResponse(RequestType.POST, "aicore/extension/thresholds",
                thresholdFile, queryParams, null, Collections.singletonMap("Content-Type", "application/xml"))) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
            float thresholdValue = Framework.getService(ThresholdService.class).getThreshold(file, DC_DESCRIPTION);
            assertThat(thresholdValue).isEqualTo(0.88f);
        }
    }

    @Test
    public void iCanRetrieveThresholds() {
        this.injectThresholds();
        try (CloseableClientResponse response = getResponse(RequestType.GET, "aicore/extension/thresholds",
                Collections.singletonMap("Accept", "application/xml"))) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
            String output = response.getEntity(String.class);
            assertThat(output).isNotNull().isNotEmpty();
            assertThat(output).contains("<thresholdConfiguration");
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    protected void injectThresholds() {
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.putSingle("docType", "File");
        try (CloseableClientResponse response = getResponse(RequestType.POST, "aicore/extension/thresholds",
                thresholdFile, queryParams, null, Collections.singletonMap("Content-Type", "application/xml"))) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        }
        queryParams.putSingle("docType", "Folder");
        try (CloseableClientResponse response = getResponse(RequestType.POST, "aicore/extension/thresholds",
                thresholdFolder, queryParams, null, Collections.singletonMap("Content-Type", "application/xml"))) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        }
    }

    @Test
    public void iCanSetRetrieveModelDefinition() {
        String id = "test";
        RuntimeModel model = modelService.getModel(id);
        assertThat(model).isNull();
        try (CloseableClientResponse response = getResponse(RequestType.POST, "aicore/extension/model", modelDefinition,
                Collections.singletonMap("Content-Type", "application/xml"))) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
            model = modelService.getModel(id);
            assertThat(model).isNotNull();
        }
        try (CloseableClientResponse response = getResponse(RequestType.GET, "aicore/extension/model/" + id,
                Collections.singletonMap("Accept", "application/xml"))) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
            String output = response.getEntity(String.class);
            assertThat(output).isNotNull().isNotEmpty();
            assertThat(output).contains("<model id=\"" + id);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}
