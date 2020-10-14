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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.configuration.ThresholdComponent;
import org.nuxeo.ai.configuration.ThresholdConfiguratorDescriptor;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.restapi.test.BaseTest;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.jaxrs.test.CloseableClientResponse;
import org.nuxeo.runtime.RuntimeService;
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
        ThresholdConfiguratorDescriptor thresholdConfiguratorDescriptor = new ThresholdConfiguratorDescriptor();
        thresholdConfiguratorDescriptor.setGlobal(0.99f);
        ThresholdConfiguratorDescriptor.Threshold threshold = new ThresholdConfiguratorDescriptor.Threshold();
        threshold.setAutocorrect(0.88f);
        threshold.setAutofillValue(0.88f);
        threshold.setValue(0.88f);
        threshold.setXpath(DC_DESCRIPTION);
        List<ThresholdConfiguratorDescriptor.Threshold> thresholds = new ArrayList<>();
        thresholds.add(threshold);
        thresholdConfiguratorDescriptor.setThresholds(thresholds);
        thresholdConfiguratorDescriptor.setType(FILE);
        List<ThresholdConfiguratorDescriptor> descs = new ArrayList<>();
        descs.add(thresholdConfiguratorDescriptor);
        String body = mapper.writeValueAsString(descs);
        try (CloseableClientResponse response = getResponse(RequestType.POST, "ai/extension/threshold", body)) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
            RuntimeService runtimeService = Framework.getRuntime();
            ThresholdComponent thresholdComponent = (ThresholdComponent) runtimeService.getComponent(
                    "org.nuxeo.ai.configuration.ThresholdComponent");
            float thresholdValue = thresholdComponent.getThreshold(file, DC_DESCRIPTION);
            assertThat(thresholdValue).isEqualTo(0.88f);
        }
    }

}
