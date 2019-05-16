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
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Consts;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.nuxeo.ai.enrichment.AbstractEnrichmentProvider;
import org.nuxeo.ai.enrichment.EnrichmentDescriptor;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;

/**
 * An enrichment provider that calls a Rest api.
 * It uses a RestClient for most of the logic to call and responds to an api.
 * Options can be passed in the init() method via the descriptor.
 */
public abstract class RestEnrichmentProvider extends AbstractEnrichmentProvider {

    protected static final Log log = LogFactory.getLog(RestEnrichmentProvider.class);
    protected RestClient client;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        client = createClient(descriptor.options);
    }

    /**
     * Creates the Rest Client during initialization
     */
    protected RestClient createClient(Map<String, String> options) {
        return new RestClient(options, null);
    }

    @Override
    public Collection<AIMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {

        return client.call(builder -> prepareRequest(builder, blobTextFromDoc),
                           response -> {
                               int statusCode = response.getStatusLine().getStatusCode();
                               if (statusCode < 200 || statusCode >= 300) {
                                   log.warn(String.format("Unsuccessful call to rest api %s, status is %d",
                                                          client.uri.toString(),
                                                          statusCode));
                                   return Collections.emptyList();
                               } else {
                                   return handleResponse(response, blobTextFromDoc);
                               }
                           }
        );

    }

    /**
     * Prepare the request prior to calling
     */
    public abstract HttpUriRequest prepareRequest(RequestBuilder builder, BlobTextFromDocument blobTextFromDoc);

    /**
     * Handle the response and return the result
     */
    public abstract Collection<AIMetadata> handleResponse(HttpResponse response, BlobTextFromDocument blobTextFromDoc);

    /**
     * Set a string as a Json body parameter on the request
     */
    protected void setJson(RequestBuilder builder, String json) {
        if (StringUtils.isNotEmpty(json)) {
            builder.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        }
    }

    /**
     * Sets a Multipart body but allows you to use the MultipartEntityBuilder first
     */
    protected void setMultipart(RequestBuilder builder, Consumer<MultipartEntityBuilder> withMultipart) {
        MultipartEntityBuilder multipartBuilder = MultipartEntityBuilder.create();
        multipartBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        if (withMultipart != null) {
            withMultipart.accept(multipartBuilder);
        }
        builder.setEntity(multipartBuilder.build());
    }

    /**
     * Get the content from the response
     */
    protected String getContent(HttpResponse response) {
        try {
            return IOUtils.toString(response.getEntity().getContent(), Consts.UTF_8);
        } catch (IOException e) {
            log.warn(String.format("Unable to read the response for api %s", client.uri.toString()), e);
            return null;
        }
    }


}
