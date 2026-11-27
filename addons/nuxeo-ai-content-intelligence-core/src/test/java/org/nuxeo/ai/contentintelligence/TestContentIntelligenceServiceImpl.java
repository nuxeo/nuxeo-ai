/*
 * (C) Copyright 2026 Hyland (http://hyland.com/) and others.
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
 */
package org.nuxeo.ai.contentintelligence;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hyland.sdk.cic.http.client.CICServiceException;
import org.hyland.sdk.cic.ke.object.EnrichmentResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Runs {@link ContentIntelligenceServiceImpl} against a local HTTP server standing for the authentication and the
 * Knowledge Enrichment endpoints, so the real SDK client (including its JSON serializer, loaded at runtime) is used.
 */
public class TestContentIntelligenceServiceImpl {

    protected static final String TOKEN = "{\"access_token\":\"token-1\",\"token_type\":\"Bearer\",\"expires_in\":3600,"
            + "\"scope\":\"environment_authorization\"}";

    protected static final String RESULTS = "{\"id\":\"proc-1\",\"status\":\"SUCCESS\",\"results\":[{\"objectKey\":"
            + "\"ok1\",\"imageDescription\":{\"isSuccess\":true,\"result\":\"A cat\"}}]}";

    protected record Response(int status, String body) {
    }

    protected HttpServer server;

    protected String baseUrl;

    protected final Map<String, Function<HttpExchange, Response>> routes = new ConcurrentHashMap<>();

    protected final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

    protected final List<String> bodies = new CopyOnWriteArrayList<>();

    protected final List<String> contentTypes = new CopyOnWriteArrayList<>();

    protected ContentIntelligenceServiceImpl service;

    @Before
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        routes.put("POST /auth/connect/token", ex -> new Response(200, TOKEN));
        routes.put("GET /ke/files/upload/presigned-url",
                ex -> new Response(200, "{\"presignedUrl\":\"" + baseUrl + "/upload/ok1\",\"objectKey\":\"ok1\"}"));
        routes.put("PUT /upload/ok1", ex -> new Response(200, ""));
        routes.put("POST /ke/content/process", ex -> new Response(200, "{\"processingId\":\"proc-1\"}"));
        AtomicInteger polls = new AtomicInteger();
        routes.put("GET /ke/content/process/proc-1/results",
                ex -> polls.incrementAndGet() == 1 ? new Response(202, "") : new Response(200, RESULTS));
        service = newService(5);
    }

    @After
    public void stopServer() {
        if (service != null) {
            service.close();
        }
        server.stop(0);
    }

    protected ContentIntelligenceServiceImpl newService(int maxTries) {
        ContentIntelligenceDescriptor desc = descriptor("default", baseUrl + "/auth", baseUrl + "/ke");
        ContentIntelligenceDescriptor incomplete = new ContentIntelligenceDescriptor();
        incomplete.name = "incomplete";
        incomplete.baseUrl = baseUrl + "/ke";
        return new ContentIntelligenceServiceImpl(List.of(desc, incomplete), maxTries, Duration.ofMillis(1));
    }

    protected static ContentIntelligenceDescriptor descriptor(String name, String authUrl, String baseUrl) {
        ContentIntelligenceDescriptor desc = new ContentIntelligenceDescriptor();
        desc.name = name;
        desc.authenticationBaseUrl = authUrl;
        desc.baseUrl = baseUrl;
        desc.tokenGrantType = "client_credentials";
        desc.tokenScope = "environment_authorization";
        desc.clientId = "client-1";
        desc.clientSecret = "secret-1";
        return desc;
    }

    protected void replaceService(ContentIntelligenceServiceImpl newService) {
        service.close();
        service = newService;
    }

    protected void handle(HttpExchange exchange) throws IOException {
        String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
        hits.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
        bodies.add(key + " " + new String(exchange.getRequestBody().readAllBytes(), UTF_8));
        contentTypes.add(key + " " + exchange.getRequestHeaders().getFirst("Content-Type") + " "
                + exchange.getRequestURI().getRawQuery());
        Function<HttpExchange, Response> route = routes.get(key);
        Response response = route == null ? new Response(404, "{\"error\":\"not_found\"}") : route.apply(exchange);
        byte[] body = response.body().getBytes(UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    protected int hits(String key) {
        AtomicInteger count = hits.get(key);
        return count == null ? 0 : count.get();
    }

    protected String body(String key) {
        String prefix = key + " ";
        return bodies.stream().filter(b -> b.startsWith(prefix)).findFirst().orElseThrow().substring(prefix.length());
    }

    protected static Blob blob() {
        return Blobs.createBlob("hello", "image/jpeg");
    }

    protected static ContentIntelligenceRequest request() {
        return ContentIntelligenceRequest.of("image-description", null, "{\"maxWordCount\":20}");
    }

    protected static int status(NuxeoException e) {
        CICServiceException cause = ExceptionUtils.throwableOfType(e, CICServiceException.class);
        assertNotNull("missing CICServiceException cause in " + ExceptionUtils.getStackTrace(e), cause);
        return cause.statusCode();
    }

    @Test
    public void shouldUploadProcessAndPollResults() throws Exception {
        EnrichmentResult result = service.enrich("default", blob(), request());

        assertEquals("proc-1", result.id());
        assertEquals("SUCCESS", result.status());
        assertEquals("A cat", result.results().get(0).imageDescription().result().value());
        assertEquals(2, hits("GET /ke/content/process/proc-1/results"));

        String token = body("POST /auth/connect/token");
        assertTrue(token, token.contains("grant_type=client_credentials"));
        assertTrue(token, token.contains("client_id=client-1"));
        assertTrue(token, token.contains("scope=environment_authorization"));
        assertTrue(contentTypes.toString(),
                contentTypes.stream()
                            .anyMatch(c -> c.startsWith("GET /ke/files/upload/presigned-url")
                                    && c.contains("contentType=image")));
        assertEquals("hello", body("PUT /upload/ok1"));
        assertTrue(contentTypes.toString(), contentTypes.contains("PUT /upload/ok1 image/jpeg null"));

        JsonNode process = new ObjectMapper().readTree(body("POST /ke/content/process"));
        assertEquals("context.api/v2", process.get("version").asText());
        assertEquals(20, process.get("actions").get("imageDescription").get("maxWordCount").asInt());
        assertTrue(process.toString(), process.get("objectKeys").toString().contains("ok1"));
    }

    @Test
    public void shouldReturnInProgressResultWhenPollingBudgetIsExhausted() {
        routes.put("GET /ke/content/process/proc-1/results", ex -> new Response(202, ""));
        service.close();
        service = newService(2);

        EnrichmentResult result = service.enrich("default", blob(), request());

        assertEquals("PROCESSING", result.status());
        assertTrue(result.inProgress());
        assertEquals(2, hits("GET /ke/content/process/proc-1/results"));
    }

    @Test
    public void shouldNotRetryRateLimitedProcessRequest() {
        routes.put("POST /ke/content/process", ex -> new Response(429, "{\"title\":\"Too Many Requests\"}"));

        NuxeoException e = assertThrows(NuxeoException.class, () -> service.enrich("default", blob(), request()));

        assertEquals(429, status(e));
        assertEquals(1, hits("POST /ke/content/process"));
    }

    @Test
    public void shouldRetryThenReportServerErrorOnResults() {
        routes.put("GET /ke/content/process/proc-1/results", ex -> new Response(500, "{\"title\":\"boom\"}"));

        NuxeoException e = assertThrows(NuxeoException.class, () -> service.enrich("default", blob(), request()));

        assertEquals(500, status(e));
        assertTrue(hits("GET /ke/content/process/proc-1/results") > 1);
    }

    @Test
    public void shouldReportNotFoundResults() {
        routes.remove("GET /ke/content/process/proc-1/results");

        NuxeoException e = assertThrows(NuxeoException.class, () -> service.enrich("default", blob(), request()));

        assertEquals(404, status(e));
    }

    @Test
    public void shouldReportAuthenticationFailure() {
        routes.put("POST /auth/connect/token", ex -> new Response(401, "{\"error\":\"invalid_client\"}"));

        NuxeoException e = assertThrows(NuxeoException.class, () -> service.enrich("default", blob(), request()));

        assertEquals(401, status(e));
        assertEquals(0, hits("PUT /upload/ok1"));
    }

    @Test
    public void shouldRefuseUnknownOrIncompleteConfiguration() {
        NuxeoException unknown = assertThrows(NuxeoException.class,
                () -> service.enrich("unknown", blob(), request()));
        assertTrue(unknown.getMessage(), unknown.getMessage().contains("'unknown'"));
        assertThrows(NuxeoException.class, () -> service.enrich("incomplete", blob(), request()));
        assertTrue(hits.isEmpty());
    }

    @Test
    public void shouldKeepPollingWhileResultIsInProgress() {
        AtomicInteger polls = new AtomicInteger();
        routes.put("GET /ke/content/process/proc-1/results", ex -> polls.incrementAndGet() == 1
                ? new Response(200, "{\"id\":\"proc-1\",\"status\":\"PROCESSING\",\"inProgress\":true,\"results\":[]}")
                : new Response(200, RESULTS));

        EnrichmentResult result = service.enrich("default", blob(), request());

        assertEquals("SUCCESS", result.status());
        assertEquals(2, hits("GET /ke/content/process/proc-1/results"));
    }

    @Test
    public void shouldWrapUnmappableResult() {
        routes.put("GET /ke/content/process/proc-1/results", ex -> new Response(200,
                "{\"id\":\"proc-1\",\"status\":\"SUCCESS\",\"results\":[{\"objectKey\":\"ok1\","
                        + "\"namedEntityText\":{\"isSuccess\":true,\"result\":{\"dates\":[2024]}}}]}"));

        NuxeoException e = assertThrows(NuxeoException.class, () -> service.enrich("default", blob(), request()));
        assertTrue(e.getMessage(), e.getMessage().contains("results retrieval"));
    }

    @Test
    public void shouldWrapUploadFailureWithoutHttpStatus() {
        routes.put("PUT /upload/ok1", ex -> new Response(403, "denied"));

        assertThrows(NuxeoException.class, () -> service.enrich("default", blob(), request()));
        assertEquals(0, hits("POST /ke/content/process"));
    }

    @Test
    public void shouldSkipConfigurationsWithInvalidUrlOrNoName() {
        ContentIntelligenceDescriptor noScheme = descriptor("noScheme", baseUrl + "/auth", "api.example.invalid/ke");
        ContentIntelligenceDescriptor noName = descriptor(null, baseUrl + "/auth", baseUrl + "/ke");
        replaceService(new ContentIntelligenceServiceImpl(
                List.of(noScheme, noName, descriptor("default", baseUrl + "/auth", baseUrl + "/ke")), 5,
                Duration.ofMillis(1)));

        NuxeoException e = assertThrows(NuxeoException.class, () -> service.enrich("noScheme", blob(), request()));
        assertTrue(e.getMessage(), e.getMessage().contains("No complete"));
        assertEquals("SUCCESS", service.enrich("default", blob(), request()).status());
    }

    @Test
    public void shouldAcceptTrailingSlashesAndBlankConfigName() {
        replaceService(new ContentIntelligenceServiceImpl(
                List.of(descriptor("default", baseUrl + "/auth/", baseUrl + "/ke/")), 5, Duration.ofMillis(-5)));

        assertEquals("SUCCESS", service.enrich(null, blob(), request()).status());
        assertEquals(1, hits("GET /ke/files/upload/presigned-url"));
        assertEquals(1, hits("POST /auth/connect/token"));
    }
}
