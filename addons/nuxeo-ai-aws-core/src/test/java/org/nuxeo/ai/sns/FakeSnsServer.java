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
package org.nuxeo.ai.sns;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Local SNS endpoint for tests, so that the subscription done at startup never reaches AWS. It records the requests and
 * answers every one of them as a successful Subscribe.
 * <p>
 * Must be started before the Nuxeo runtime, e.g. from a static initializer of the test class.
 */
public final class FakeSnsServer {

    /** AWS SDK system property overriding the SNS endpoint. */
    public static final String SNS_ENDPOINT_PROPERTY = "aws.endpointUrlSns";

    public static final String SUBSCRIPTION_ARN = "arn:aws:sns:us-east-1:123456789012:test-topic:subscription";

    protected static final String SUBSCRIBE_RESPONSE = """
            <SubscribeResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/">
              <SubscribeResult><SubscriptionArn>%s</SubscriptionArn></SubscribeResult>
              <ResponseMetadata><RequestId>test-request</RequestId></ResponseMetadata>
            </SubscribeResponse>
            """.formatted(SUBSCRIPTION_ARN);

    protected static final List<Map<String, String>> REQUESTS = new CopyOnWriteArrayList<>();

    protected static HttpServer server;

    private FakeSnsServer() {
        // utility class
    }

    public static synchronized void start() {
        if (server != null) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", FakeSnsServer::handle);
        server.start();
        System.setProperty(SNS_ENDPOINT_PROPERTY, "http://localhost:" + server.getAddress().getPort());
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        System.clearProperty(SNS_ENDPOINT_PROPERTY);
        REQUESTS.clear();
    }

    /**
     * @return the received requests, as their form parameters (Action, TopicArn, Protocol, Endpoint...)
     */
    public static List<Map<String, String>> getRequests() {
        return REQUESTS;
    }

    protected static void handle(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            REQUESTS.add(parseForm(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        }
        byte[] response = SUBSCRIBE_RESPONSE.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/xml");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }

    protected static Map<String, String> parseForm(String body) {
        Map<String, String> params = new HashMap<>();
        for (String pair : body.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                params.put(URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
            }
        }
        return params;
    }
}
