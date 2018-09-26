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
package org.nuxeo.ai.model.serving;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentService;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.model.ModelProperty;
import org.nuxeo.ai.rest.RestClient;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ai.pipes.types.BlobTextStream;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A runtime model that calls TensorFlow Serving rest api
 */
public class TFRuntimeModel extends AbstractRuntimeModel implements EnrichmentService {

    public static final String VERB_CLASSIFY = "classify";

    public static final String VERB_REGRESS = "regress";

    public static final String VERB_PREDICT = "predict";

    public static final String PREDICTION_CUSTOM = "/prediction/custommodel";

    public static final String KIND_CONFIG = "kind";

    public static final String USE_LABELS = "useLabels";

    public static final String JSON_RESULTS = "results";

    public static final String JSON_OUTPUTS = "output_names";

    public static final String JSON_LABELS = "_labels";

    public static final String JSON_PROBABILITIES = "_prob";

    protected RestClient client;

    protected Set<String> inputNames;

    protected String kind;

    protected boolean useLabels;  //Indicates if enrichment should use suggestion or labels

    @Override
    public void init(ModelDescriptor descriptor) {
        super.init(descriptor);
        client = new RestClient(descriptor.configuration, "", null);
        useLabels = Boolean.parseBoolean(descriptor.configuration.getOrDefault(USE_LABELS, Boolean.TRUE.toString()));
        kind = descriptor.configuration.getOrDefault(KIND_CONFIG, PREDICTION_CUSTOM);
        inputNames = inputs.stream().map(ModelProperty::getName).collect(Collectors.toSet());
    }

    @Override
    public EnrichmentMetadata predict(DocumentModel doc) {
        return predict(getProperties(doc), doc.getRepositoryName(), doc.getId(), true);
    }

    /**
     * For the supplied input values try to predict a result or return null
     */
    public EnrichmentMetadata predict(Map<String, Serializable> inputValues, String repositoryName, String docRef,
                                      boolean isSuggestion) {
        return client.call(builder -> prepareRequest(VERB_PREDICT, builder, inputValues),
                           response -> {
                               int statusCode = response.getStatusLine().getStatusCode();
                               if (statusCode < HttpStatus.SC_OK || statusCode >= HttpStatus.SC_MULTIPLE_CHOICES) {
                                   log.warn(String.format("Unsuccessful call to custom model (%s), status is %d",
                                                          getName(), statusCode));
                                   return null;
                               } else {
                                   EnrichmentMetadata meta = handlePredict(response, repositoryName, docRef, isSuggestion);
                                   if (log.isDebugEnabled()) {
                                       log.debug("Prediction is " + MAPPER.writeValueAsString(meta));
                                   }
                                   return meta;
                               }
                           }
        );
    }

    /**
     * Handle the response from Tensorflow serving and return normalized EnrichmentMetadata.
     */
    protected EnrichmentMetadata handlePredict(HttpResponse response, String repositoryName, String docRef,
                                               boolean isSuggestion) {
        String content = client.getContent(response);
        EnrichmentMetadata.Builder builder =
                new EnrichmentMetadata.Builder(Instant.now(), getKind(), getId(),
                                               new AIMetadata.Context(repositoryName, docRef, null, inputNames));
        Map<String, List<EnrichmentMetadata.Label>> labelledResults = parseResponse(content);
        if (!labelledResults.isEmpty()) {
            if (isSuggestion) {
                List<EnrichmentMetadata.Suggestion> suggestions = new ArrayList<>();
                labelledResults.forEach((output, labels) ->
                                                suggestions.add(new EnrichmentMetadata.Suggestion(output, labels)));
                builder.withSuggestions(suggestions);
            } else {
                if (labelledResults.size() != 1) {
                    log.error("Multiple outputs is currently unsupported.  The output name will be lost.");
                }
                labelledResults.forEach((output, labels) -> builder.withLabels(labels));
            }
            return builder.withRawKey(saveJsonAsRawBlob(content)).build();
        }

        return null;
    }

    /**
     * Parse the json response
     */
    protected Map<String, List<EnrichmentMetadata.Label>> parseResponse(String content) {

        Map<String, List<EnrichmentMetadata.Label>> results = new HashMap<>();

        try {
            JsonNode jsonResponse = MAPPER.readTree(content);
            jsonResponse.get(JSON_RESULTS).elements().forEachRemaining(resultsNode -> {
                resultsNode.get(JSON_OUTPUTS).elements().forEachRemaining(outputNode -> {
                    String outputName = outputNode.asText();
                    ArrayNode outputProbabilities = (ArrayNode) resultsNode.get(outputName + JSON_PROBABILITIES);
                    ArrayNode outputLabels = (ArrayNode) resultsNode.get(outputName + JSON_LABELS);
                    List<EnrichmentMetadata.Label> labels = new ArrayList<>();
                    if (outputLabels.size() == outputProbabilities.size()) {
                        for (int i = 0; i < outputLabels.size(); i++) {
                            float confidence = outputProbabilities.get(i).floatValue();
                            if (confidence > minConfidence) {
                                labels.add(new EnrichmentMetadata.Label(outputLabels.get(i).asText(), confidence));
                            }
                        }
                    }
                    if (!labels.isEmpty()) {
                        results.put(outputName, labels);
                    }
                });

            });
        } catch (NullPointerException | IOException e) {
            log.warn(String.format("Unable to read the json response: %s", content), e);
        }
        return results;
    }

    /**
     * Prepares the http request to send to Tensorflow serving
     */
    protected HttpUriRequest prepareRequest(String verb, RequestBuilder builder, Map<String, Serializable> inputs) {
        builder.setUri(buildUri(verb, builder.getUri().toString()));

        try {
            String json = MAPPER.writeValueAsString(new TensorInstances(inputs));
            builder.setEntity(EntityBuilder.create().setText(json).build());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize model inputs", e);
            throw new NuxeoException("Unable to make a valid json request", e);
        }

        return builder.build();
    }

    /**
     * Builds the uri.
     */
    protected String buildUri(String verb, String baseUri) {
        return baseUri + verb;
    }

    @Override
    public String getName() {
        String version = getVersion();
        return super.getName() + (StringUtils.isNotBlank(version) ? "_" + getVersion() : "");
    }

    @Override
    public String getKind() {
        return kind;
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextStream bts) {
        ManagedBlob blob = bts.getBlob();
        Map<String, Serializable> inputProperties;
        if (blob != null && bts.getXPaths().size() == 1) {
            inputProperties = singletonMap(bts.getXPaths().iterator().next(), convertImageBlob(blob));
        } else if (bts.getText() != null) {
            inputProperties = singletonMap(bts.getXPaths().iterator().next(), bts.getText());
        } else {
            inputProperties = new HashMap<>(bts.getProperties());
        }
        if (inputProperties.isEmpty()) {
            log.warn(String.format("(%s) unable to enrich doc properties for doc %s", getName(), bts.getId()));
            return emptyList();
        } else {
            return Collections.singletonList(predict(inputProperties, bts.getRepositoryName(), bts.getId(), useLabels));
        }
    }

    @Override
    protected Serializable convertImageBlob(Blob sourceBlob) {
        Serializable converted = super.convertImageBlob(sourceBlob);
        if (converted instanceof String) {
            return new TensorImage((String) converted);
        }
        return null;
    }


    /**
     * A representation of Tensorflow instance parameters
     */
    protected static class TensorInstances {
        public final List<Map<String, Serializable>> instances = new ArrayList<>();

        public TensorInstances(Map<String, Serializable> inputs) {
            instances.add(inputs);
        }
    }

    /**
     * A representation of a base 64 encoded image for serialization
     */
    protected static class TensorImage implements Serializable {
        private static final long serialVersionUID = 2603715122387085509L;

        public final String b64;

        public TensorImage(String b64) {
            this.b64 = b64;
        }
    }
}
