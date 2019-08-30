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

import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;

/**
 * An implementation of a service that calls a rest api
 */
public class RestEnrichmentImpl extends RestEnrichmentService {

    @Override
    protected RestClient createClient(Map<String, String> options) {
        return new RestClient(options, httpClientBuilder ->
                httpClientBuilder
                .disableAutomaticRetries()
                .disableCookieManagement()
                .build());
    }

    @Override
    public HttpUriRequest prepareRequest(RequestBuilder builder, BlobTextFromDocument blobTextFromDoc) {
        builder.setHeader("nuxeo", "isGreat");
        setMultipart(builder, multipartEntityBuilder -> multipartEntityBuilder.addTextBody("test", "ai"));
        return builder.build();
    }

    @Override
    public Collection<EnrichmentMetadata> handleResponse(HttpResponse response, BlobTextFromDocument blobTextFromDoc) {
        String content = getContent(response);
        String rawKey = saveJsonAsRawBlob(content);
        List<EnrichmentMetadata.Label> labels = new ArrayList<>();

        try {
            MAPPER.readTree(content).fieldNames()
                  .forEachRemaining(s -> labels.add(new EnrichmentMetadata.Label(s, 1, 0L)));
        } catch (IOException e) {
            log.warn(String.format("Unable to read the json response: %s", content), e);
        }

        return Collections.singletonList(
                new EnrichmentMetadata.Builder(kind,
                                               name,
                                               blobTextFromDoc)
                        .withLabels(asLabels(labels))
                        .withRawKey(rawKey)
                        .build());
    }
}
