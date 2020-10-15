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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.configuration.ThresholdService;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.restapi.test.BaseTest;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.jaxrs.test.CloseableClientResponse;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(RestServerFeature.class)
@Deploy({ "org.nuxeo.ai.ai-core", "org.nuxeo.ai.ai-config" })
public class TestAIConfigREST extends BaseTest {

    public static final String DC_DESCRIPTION = "dc:description";

    public static final String FILE = "File";

    @Test
    public void iCanSetNuxeoConfVar() throws IOException {
        assertThat(Framework.getProperty("test")).isNullOrEmpty();
        Map<String, String> confVar = new HashMap<>();
        confVar.put("test", "some value");
        String body = mapper.writeValueAsString(confVar);
        try (CloseableClientResponse response = getResponse(BaseTest.RequestType.POST, "ai/config", body)) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
            assertThat(Framework.getProperty("test")).isNotNull().isNotEmpty();
        }
    }

    @Test
    public void iCanSetThreshold() throws IOException {
        DocumentModel file = session.createDocumentModel("/", "file", FILE);
        file = session.createDocument(file);

        String threshold = "<thresholdConfiguration type=\"File\"\n" + //
                "                            global=\"0.99\">\n" + //
                "      <thresholds>\n" + //
                "        <threshold xpath=\"dc:description\"\n" + //
                "                   value=\"0.88\"\n" + //
                "                   autofill=\"0.76\"\n" + //
                "                   autocorrect=\"0.77\"/>\n" + //
                "      </thresholds>\n" + //
                "    </thresholdConfiguration>";

        try (CloseableClientResponse response = getResponse(RequestType.POST, "ai/extension/thresholds", threshold,
                Collections.singletonMap("Content-Type", "application/xml"))) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
            float thresholdValue = Framework.getService(ThresholdService.class).getThreshold(file, DC_DESCRIPTION);
            assertThat(thresholdValue).isEqualTo(0.88f);
        }
    }

}