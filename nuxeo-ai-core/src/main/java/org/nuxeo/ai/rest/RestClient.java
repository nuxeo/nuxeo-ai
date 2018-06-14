/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 * Contributors:
 *     Gethin James
 */
package org.nuxeo.ai.rest;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * A basic rest client optimized for calling Json rest services.
 */
public class RestClient implements AutoCloseable {
    private static final Log log = LogFactory.getLog(RestClient.class);

    protected final String method;
    protected final String contentType;
    protected final String accept;
    protected final List<Header> headers = new ArrayList<>();
    protected final URI uri;
    protected CloseableHttpClient client;

    public RestClient(Map<String, String> options, Function<HttpClientBuilder, CloseableHttpClient> clientBuilderFunc) {
        contentType = options.getOrDefault("contentType", ContentType.APPLICATION_JSON.getMimeType());
        accept = options.getOrDefault("accept", ContentType.APPLICATION_JSON.getMimeType());
        method = options.getOrDefault("methodName", HttpPost.METHOD_NAME);
        String uriParam = options.get("uri");
        if (StringUtils.isBlank(uriParam)) {
            throw new NuxeoException("You must specify a URI");
        } else {
            uri = URI.create(uriParam);
        }
        headers.addAll(defaultHeaders());
        HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        client = clientBuilderFunc != null ? clientBuilderFunc.apply(clientBuilder) : clientBuilder.build();
    }

    public <T> T call(Function<RequestBuilder, HttpUriRequest> requestBuilderFunc, ResponseHandler<T> handler) {
        RequestBuilder requestBuilder = RequestBuilder.create(method).setUri(uri);
        headers.forEach(requestBuilder::addHeader);
        HttpUriRequest request =
                requestBuilderFunc != null ? requestBuilderFunc.apply(requestBuilder) : requestBuilder.build();

        try (CloseableHttpResponse response = getClient().execute(request)) {
            if (response != null && handler != null) {
                return handler.handleResponse(response);
            }
        } catch (IOException e) {
            log.warn(String.format("Unsuccessful call to rest api %s", uri.toString()), e);
        }
        return null;
    }

    protected CloseableHttpClient getClient() {
        return client;
    }

    public List<Header> defaultHeaders() {
        return Arrays.asList(new BasicHeader(HttpHeaders.CACHE_CONTROL, "no-cache"),
                             new BasicHeader(HttpHeaders.CONTENT_TYPE, contentType),
                             new BasicHeader(HttpHeaders.ACCEPT, accept)
        );
    }

    @Override
    public void close() throws Exception {
        CloseableHttpClient aClient = getClient();
        if (aClient != null) {
            aClient.close();
        }
    }
}
