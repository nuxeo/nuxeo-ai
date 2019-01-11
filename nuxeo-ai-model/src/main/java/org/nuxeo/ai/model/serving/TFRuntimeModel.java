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
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

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
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentService;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.Suggestion;
import org.nuxeo.ai.metadata.SuggestionMetadata;
import org.nuxeo.ai.model.ModelProperty;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

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

    public static final String MODEL_LABEL = "modelLabel";

    public static final String JSON_RESULTS = "results";

    public static final String JSON_OUTPUTS = "output_names";

    public static final String JSON_LABELS = "_labels";

    public static final String JSON_PROBABILITIES = "_prob";

    protected Set<String> inputNames;

    protected String kind;

    protected String modelPath = "";

    protected boolean useLabels;  //Indicates if enrichment should use suggestion or labels

    @Override
    public void init(ModelDescriptor descriptor) {
        super.init(descriptor);
        useLabels = Boolean.parseBoolean(descriptor.configuration.getOrDefault(USE_LABELS, Boolean.TRUE.toString()));
        kind = descriptor.configuration.getOrDefault(KIND_CONFIG, PREDICTION_CUSTOM);
        inputNames = inputs.stream().map(ModelProperty::getName).collect(Collectors.toSet());
        String modelLabel = descriptor.info.get(MODEL_LABEL);
        if (isBlank(modelLabel)) {
            log.debug("No " + MODEL_LABEL + " has been specified for model " + descriptor.id);
        } else {
            modelPath =  modelLabel + "/";
        }
    }

    @Override
    public SuggestionMetadata predict(DocumentModel doc) {
        return predict(getProperties(doc));
    }

    /**
     * For the supplied input values try to predict a result or return null
     */
    public SuggestionMetadata predict(Map<String, Serializable> inputValues) {
        CloudClient client = Framework.getService(CloudClient.class);
        if (client.isAvailable()) {
            String json = prepareRequest(inputValues);
            if (isNotBlank(json)) {
                return client.post(buildUri(), json,
                                   response -> {
                                       SuggestionMetadata meta = handlePredict(response.body().string());
                                       if (log.isDebugEnabled()) {
                                           log.debug(getName()+  " prediction is " + MAPPER.writeValueAsString(meta));
                                       }
                                       return meta;
                                   }
                );
            }
        }

        return null;
    }

    /**
     * Handle the response from Tensorflow serving and return normalized SuggestionMetadata.
     */
    protected SuggestionMetadata handlePredict(String content) {
        Map<String, List<EnrichmentMetadata.Label>> labelledResults = parseResponse(content);
        if (!labelledResults.isEmpty()) {
            SuggestionMetadata.Builder builder = new SuggestionMetadata.Builder(getKind(), getId(), inputNames);
            List<Suggestion> suggestions = new ArrayList<>();
            labelledResults.forEach((output, labels) ->
                                            suggestions.add(new Suggestion(output, labels)));
            builder.withSuggestions(suggestions);
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
    protected String prepareRequest(Map<String, Serializable> inputs) {
        try {
            return MAPPER.writeValueAsString(new TensorInstances(inputs));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize model inputs", e);
            throw new NuxeoException("Unable to make a valid json request", e);
        }
    }

    /**
     * Builds the uri.
     */
    protected String buildUri() {
        return String.format("/model/%s/", getName()) + modelPath + VERB_PREDICT;
    }

    @Override
    public String getKind() {
        return kind;
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobtext) {
        Map<String, Serializable> inputProperties = new HashMap<>();

        for (Map.Entry<String, ManagedBlob> blobEntry : blobtext.getBlobs().entrySet()) {
            inputProperties.put(blobEntry.getKey(), convertImageBlob(blobEntry.getValue()));
        }
        inputProperties.putAll(blobtext.getProperties());
        if (inputProperties.isEmpty()) {
            log.warn(String.format("(%s) unable to enrich doc properties for doc %s", getName(), blobtext.getId()));
        } else {
            SuggestionMetadata suggestions = predict(inputProperties);
            if (suggestions != null && !suggestions.getSuggestions().isEmpty()) {
                EnrichmentMetadata.Builder builder =
                        new EnrichmentMetadata.Builder(Instant.now(), getKind(), getId(),
                                                       new AIMetadata.Context(
                                                               blobtext.getRepositoryName(), blobtext.getId(),
                                                               null, inputNames));
                builder.withRawKey(suggestions.getRawKey());
                if (useLabels) {
                    if (suggestions.getSuggestions().size() != 1) {
                        log.error("Multiple outputs is currently unsupported.  The output name will be lost.");
                    }
                    List<AIMetadata.Label> vals = suggestions.getSuggestions().stream()
                                                             .map(Suggestion::getValues)
                                                             .flatMap(Collection::stream)
                                                             .collect(Collectors.toList());
                    builder.withLabels(vals);
                } else {
                    builder.withSuggestions(suggestions.getSuggestions());
                }
                return Collections.singletonList(builder.build());

            }
        }
        return emptyList();
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
