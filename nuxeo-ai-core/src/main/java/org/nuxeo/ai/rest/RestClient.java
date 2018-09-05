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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
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

    public static final String OPTION_CONTENT_TYPE = "contentType";

    public static final String OPTION_URI = "uri";

    public static final String OPTION_ACCEPT = "accept";

    public static final String OPTION_METHOD_NAME = "methodName";

    public static final String OPTION_DEFAULT_HEADERS = "headers.default";

    private static final Log log = LogFactory.getLog(RestClient.class);

    protected final String method;

    protected final String contentType;

    protected final String accept;

    protected final List<Header> headers = new ArrayList<>();

    protected final URI uri;

    protected CloseableHttpClient client;

    /**
     * Create a rest client with the specified options
     */
    public RestClient(Map<String, String> options, Function<HttpClientBuilder, CloseableHttpClient> clientBuilderFunc) {
        this(options, "", clientBuilderFunc);
    }

    /**
     * Create a rest client with the specified options and a prefix that will be used when getting the options
     */
    public RestClient(Map<String, String> options, String optionPrefix,
                      Function<HttpClientBuilder, CloseableHttpClient> clientBuilderFunc) {
        contentType = options
                .getOrDefault(optionPrefix + OPTION_CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        accept = options.getOrDefault(optionPrefix + OPTION_ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
        method = options.getOrDefault(optionPrefix + OPTION_METHOD_NAME, HttpPost.METHOD_NAME);
        String uriParam = options.get(optionPrefix + OPTION_URI);
        if (StringUtils.isBlank(uriParam)) {
            throw new NuxeoException("You must specify a URI");
        } else {
            uri = URI.create(uriParam);
        }
        if (Boolean.parseBoolean(options.getOrDefault(optionPrefix + OPTION_DEFAULT_HEADERS, "true"))) {
            headers.addAll(getDefaultHeaders());
        }
        HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        client = clientBuilderFunc != null ? clientBuilderFunc.apply(clientBuilder) : clientBuilder.build();
    }

    /**
     * Checks to see if the uri specified returns successfully
     * You can specify an optional prefix for the url options.
     */
    public static boolean isLive(Map<String, String> options, String prefix) {
        try (RestClient restClient = new RestClient(options, prefix, null)) {
            Boolean callResult = restClient.call(null, response -> {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode < HttpStatus.SC_OK || statusCode >= HttpStatus.SC_MULTIPLE_CHOICES) {
                    log.info(String.format("Live check failed for %s, status is %d", options.get("uri"), statusCode));
                    return false;
                }
                return true;
            });
            if (Boolean.TRUE.equals(callResult)) {
                return true;
            }
        } catch (IOException e) {
            log.info("Error on rest client ", e);
        }
        return false;
    }

    /**
     * Make the http request and handle the response.
     * You can use an optional request builder and response handler.
     */
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
            log.info(String.format("Unsuccessful call to rest api %s", uri.toString()), e);
        }
        return null;
    }

    protected CloseableHttpClient getClient() {
        return client;
    }

    /**
     * Gets the default headers to add to the request
     */
    public List<Header> getDefaultHeaders() {
        return Arrays.asList(new BasicHeader(HttpHeaders.CACHE_CONTROL, "no-cache"),
                             new BasicHeader(HttpHeaders.CONTENT_TYPE, contentType),
                             new BasicHeader(HttpHeaders.ACCEPT, accept)
        );
    }

    /**
     * Gets the http response content as a String
     */
    public String getContent(HttpResponse response) {
        try {
            return IOUtils.toString(response.getEntity().getContent(), Consts.UTF_8);
        } catch (IOException e) {
            log.warn("Unable to read the response.", e);
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        CloseableHttpClient aClient = getClient();
        if (aClient != null) {
            aClient.close();
        }
    }
}
