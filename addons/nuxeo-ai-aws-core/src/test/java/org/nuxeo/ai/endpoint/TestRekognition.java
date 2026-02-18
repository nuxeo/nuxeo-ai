package org.nuxeo.ai.endpoint;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import jakarta.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.webengine.test.WebEngineFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.ServletContainerFeature;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RunWith(FeaturesRunner.class)
@Features({ WebEngineFeature.class, CoreFeature.class })
@Deploy("org.nuxeo.ai.aws.aws-core")
@Deploy("org.nuxeo.ecm.platform.web.common")
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestRekognition {

    private static final String CONTENT_TYPE = "application/json";

    private static final Integer TIMEOUT = 1000 * 60 * 5; // 5min

    protected HttpClient httpClient;

    @Inject
    protected ServletContainerFeature servletContainerFeature;

    @Inject
    protected EventService eventService;

    @Before
    public void setup() {
        httpClient = HttpClient.newHttpClient();
    }

    @Test
    public void shouldCreateNotification() throws IOException, InterruptedException {
        ArrayNode arrayNode = new ArrayNode(JsonNodeFactory.instance);
        arrayNode.add(new ObjectNode(JsonNodeFactory.instance));

        File jsonPayload = FileUtils.getResourceFileFromContext("sns-success-resp.json");
        byte[] jsonData = Files.readAllBytes(Paths.get(jsonPayload.toURI()));
        String jsonPost = new String(jsonData, StandardCharsets.UTF_8);
        var request = java.net.http.HttpRequest.newBuilder()
                                               .uri(URI.create(getBaseURL() + "/aiaddons/rekognition/callback/labels"))
                                               .header("Content-Type", CONTENT_TYPE)
                                               .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPost))
                                               .build();
        var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        eventService.waitForAsyncCompletion();
    }

    protected String getBaseURL() {
        int port = servletContainerFeature.getPort();
        return "http://localhost:" + port;
    }
}
