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
package org.nuxeo.ai.custom;

import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.MAPPER;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.http.Consts;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.nuxeo.ai.enrichment.EnrichmentDescriptor;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.rest.RestEnrichmentService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobMeta;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Enriches using a custom model which is accessed via a rest call
 */
public class CustomModelEnrichmentService extends RestEnrichmentService {

    public static final String DEFAULT_MODEL = "dnn";
    public static final String DEFAULT_IMAGE_FEATURE = "image";
    public static final String DEFAULT_TEXT_FEATURE = "text";
    public static final String DEFAULT_CONFIDENCE = "0.7";
    protected String modelName;
    protected String imageFeatureName;
    protected String textFeatureName;
    protected float minConfidence;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        modelName = descriptor.options.getOrDefault("modelName", DEFAULT_MODEL);
        imageFeatureName = descriptor.options.getOrDefault("imageFeatureName", DEFAULT_IMAGE_FEATURE);
        textFeatureName = descriptor.options.getOrDefault("textFeatureName", DEFAULT_TEXT_FEATURE);
        minConfidence = Float.parseFloat(descriptor.options.getOrDefault("minConfidence", DEFAULT_CONFIDENCE));
    }

    @Override
    public HttpUriRequest prepareRequest(RequestBuilder builder, BlobTextStream blobTextStream) {
        try {
            List<Feature> features = new ArrayList<>();
            BlobMeta blob = blobTextStream.getBlob();
            String text = blobTextStream.getText();

            if (blob != null) {
                features.add(new Feature(imageFeatureName, "image",
                                         new Feature.Content(null, null, blob.getMimeType(),
                                                             blob.getEncoding(), blob.getLength())));
                builder.setEntity(EntityBuilder.create().setStream(readBlob(blob)).build());
                builder.setHeader(HttpHeaders.CONTENT_TYPE, blob.getMimeType());
            }
            if (StringUtils.isNotEmpty(text)) {
                features.add(new Feature(textFeatureName, "text", text,
                                         Consts.UTF_8.toString(), (long) text.length()));
            }

            String json = MAPPER.writeValueAsString(features);
            builder.setHeader("modelName", modelName);
            builder.setHeader("features", json);
            return builder.build();
        } catch (IOException e) {
            throw new NuxeoException("Unable to make a valid json request", e);
        }
    }

    @Override
    public Collection<EnrichmentMetadata> handleResponse(HttpResponse response, BlobTextStream blobTextStream) {
        String content = getContent(response);
        String rawKey = saveJsonAsRawBlob(content);
        List<EnrichmentMetadata.Label> labels = new ArrayList<>();

        //TODO: Write a better results json format
        try {
            JsonNode jsonTree = MAPPER.readTree(content);
            jsonTree.get("results").elements().forEachRemaining(jsonNode -> {
                String label = jsonNode.get(0).asText();
                float confidence = jsonNode.get(1).floatValue();
                if (confidence > minConfidence) {
                    labels.add(new EnrichmentMetadata.Label(label, confidence));
                }
            });
        } catch (NullPointerException | IOException e) {
            log.warn(String.format("Unable to read the json response: %s", content), e);
        }

        if (!labels.isEmpty()) {
            return Collections.singletonList(
                    new EnrichmentMetadata.Builder(kind,
                                                   name,
                                                   blobTextStream)
                            .withRawKey(rawKey)
                            .withLabels(labels)
                            .build());
        } else {
            return Collections.emptyList();
        }

    }

    /**
     * Model features request designed to be serialized as JSON
     */
    protected static class Feature {

        public final String name;
        public final String kind;
        public final Content content;

        public Feature(String name, String kind, Content content) {
            this.name = name;
            this.kind = kind;
            this.content = content;
        }

        public Feature(String name, String kind, String text, String encoding, Long length) {
            this.name = name;
            this.kind = kind;
            this.content = new Content(text, null, null, encoding, length);
        }

        protected static class Content {
            public final String text;
            public final URI uri;
            public final String mimeType;
            public final String encoding;
            public final Long length;

            private Content(String text, URI uri, String mimeType, String encoding, Long length) {
                this.text = text;
                this.uri = uri;
                this.mimeType = mimeType;
                this.encoding = encoding;
                this.length = length;
            }
        }
    }

}
