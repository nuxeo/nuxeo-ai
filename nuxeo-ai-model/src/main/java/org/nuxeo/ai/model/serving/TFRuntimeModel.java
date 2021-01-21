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
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.nuxeo.ai.cloud.NuxeoCloudClient.API_AI;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.AI_BLOB_MAX_SIZE_CONF_VAR;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.AI_BLOB_MAX_SIZE_VALUE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.CATEGORY_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.IMAGE_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.LIST_DELIMITER_PATTERN;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TEXT_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.getPictureConversion;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.getPropertyValue;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

import java.io.IOException;
import java.io.Serializable;
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
import org.nuxeo.ai.enrichment.EnrichmentProvider;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ai.model.ModelProperty;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.pipes.types.PropertyType;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;

import io.dropwizard.metrics5.Timer;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * A runtime model that calls TensorFlow Serving rest api
 */
public class TFRuntimeModel extends AbstractRuntimeModel implements EnrichmentProvider {

    public static final String VERB_PREDICT = "predict";

    public static final String PREDICTION_CUSTOM = "/prediction/custommodel";

    public static final String KIND_CONFIG = "kind";

    public static final String MODEL_LABEL = "modelLabel";

    public static final String JSON_RESULTS = "results";

    public static final String JSON_OUTPUTS = "output_names";

    public static final String JSON_LABELS = "_labels";

    protected Set<String> inputNames;

    protected String kind;

    protected String modelPath = "";

    @Override
    public void init(ModelDescriptor descriptor) {
        super.init(descriptor);
        kind = descriptor.configuration.getOrDefault(KIND_CONFIG, PREDICTION_CUSTOM);
        inputNames = inputs.stream().map(ModelProperty::getName).collect(Collectors.toSet());
        String modelLabel = descriptor.info.get(MODEL_LABEL);
        if (isBlank(modelLabel)) {
            log.debug("No " + MODEL_LABEL + " has been specified for model " + descriptor.id);
        } else {
            modelPath = modelLabel + "/";
        }
    }

    /**
     * For the supplied input values try to predict a result or return null
     */
    public EnrichmentMetadata predict(Map<String, Tensor> inputValues, String repositoryName, String documentRef) {
        Timer.Context responseTime = aiComponent.getMetrics().getInsightPredictionTime().time();
        try {
            if (inputValues.size() != inputs.size()) {
                log.debug(getName() + " did not call prediction.  Properties provided were " + inputValues.keySet());
                return null;
            }
            CloudClient client = Framework.getService(CloudClient.class);
            if (client.isAvailable()) {
                TensorInstances tensorInstances = new TensorInstances(documentRef, inputValues);
                String json = prepareRequest(tensorInstances);
                if (isNotBlank(json)) {
                    String uri = buildUri(client);
                    return client.post(uri, json, response -> {
                        if (response.isSuccessful() && response.body() != null) {
                            EnrichmentMetadata meta = handlePredict(response.body().string(), repositoryName,
                                    documentRef);
                            if (log.isDebugEnabled()) {
                                log.debug(getName() + ": prediction metadata is: " + MAPPER.writeValueAsString(meta));
                            }
                            return meta;
                        } else {
                            log.warn(String.format("Unsuccessful call to (%s), status is %d", uri, response.code()));
                            return null;
                        }
                    });
                }
            }

            return null;
        } finally {
            responseTime.stop();
        }
    }

    /**
     * Handle the response from Tensorflow serving and return normalized EnrichmentMetadata.
     */
    protected EnrichmentMetadata handlePredict(String content, String repositoryName, String documentRef) {
        Map<String, List<EnrichmentMetadata.Label>> labelledResults = parseResponse(content);
        if (!labelledResults.isEmpty()) {
            EnrichmentMetadata.Builder builder = new EnrichmentMetadata.Builder(kind, getId(), inputNames,
                    repositoryName, documentRef, Collections.emptySet());
            List<LabelSuggestion> labelSuggestions = new ArrayList<>();
            labelledResults.forEach((output, labels) -> labelSuggestions.add(new LabelSuggestion(output, labels)));
            return builder.withLabels(labelSuggestions)
                          .withRawKey(saveJsonAsRawBlob(content))
                          .withModelVersion(getVersion())
                          .build();
        }
        return null;
    }

    /**
     * Parse the json response
     */
    protected Map<String, List<EnrichmentMetadata.Label>> parseResponse(String content) {

        Map<String, List<EnrichmentMetadata.Label>> results = new HashMap<>();

        try {
            if (log.isDebugEnabled()) {
                log.debug(getName() + ": response is: " + content);
            }
            JsonNode jsonResponse = MAPPER.readTree(content);
            jsonResponse.get(JSON_RESULTS)
                        .elements()
                        .forEachRemaining(
                                resultsNode -> resultsNode.get(JSON_OUTPUTS).elements().forEachRemaining(outputNode -> {
                                    String outputName = outputNode.asText();
                                    ArrayNode outputProbabilities = (ArrayNode) resultsNode.get(outputName);
                                    ArrayNode outputLabels = (ArrayNode) resultsNode.get(outputName + JSON_LABELS);
                                    List<EnrichmentMetadata.Label> labels = new ArrayList<>();
                                    if (outputLabels.size() == outputProbabilities.size()) {
                                        for (int i = 0; i < outputLabels.size(); i++) {
                                            float confidence = outputProbabilities.get(i).floatValue();
                                            if (confidence > minConfidence) {
                                                labels.add(new EnrichmentMetadata.Label(outputLabels.get(i).asText(),
                                                        confidence, 0L));
                                            }
                                        }
                                    } else {
                                        log.warn("Mismatch of labels and probabilities cardinality");
                                    }
                                    if (!labels.isEmpty()) {
                                        results.put(outputName, labels);
                                    }
                                }));
        } catch (NullPointerException | IOException e) {
            log.warn(String.format("Unable to read the json response: %s", content), e);
        }
        return results;
    }

    /**
     * Prepares the http request to send to Tensorflow serving
     */
    protected String prepareRequest(TensorInstances instances) {
        try {
            return MAPPER.writeValueAsString(instances);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize model inputs", e);
            throw new NuxeoException("Unable to make a valid json request", e);
        }
    }

    /**
     * Builds the uri.
     *
     * @param client
     */
    protected String buildUri(CloudClient client) {
        String datasource = client.getCloudConfig().getDatasource();
        return API_AI + client.byProjectId(
                "/model/" + getName() + "/" + datasource + "/" + VERB_PREDICT + "?datasource=" + datasource);
    }

    @Override
    public String getKind() {
        return kind;
    }

    @Override
    public Set<String> getInputNames() {
        return inputNames;
    }

    @Override
    public EnrichmentMetadata predict(DocumentModel doc) {
        Timer.Context preConversionTime = aiComponent.getMetrics().getInsightPreConversionTime().time();
        try {
            Map<String, Tensor> props = new HashMap<>(inputs.size());
            for (ModelProperty input : inputs) {
                String type = input.getType() == null ? "none" : input.getType();
                switch (type) {
                case IMAGE_TYPE:
                    Blob blob = getPropertyValue(doc, input.getName(), Blob.class);
                    if (blob == null) {
                        return null;
                    }
                    // Get rendition if it exists
                    Blob rendition = getPictureConversion(doc, (ManagedBlob) blob);
                    // If Blob size is too big, just abort by returning null
                    if (rendition.getLength() < Long.parseLong(
                            Framework.getProperty(AI_BLOB_MAX_SIZE_CONF_VAR, AI_BLOB_MAX_SIZE_VALUE))) {
                        props.put(input.getName(), Tensor.image(convertImageBlob(rendition)));
                    } else {
                        return null;
                    }
                    break;
                case TEXT_TYPE:
                    Serializable propVal = getPropertyValue(doc, input.getName());
                    if (propVal instanceof Blob) {
                        String text = convertTextBlob(getPropertyValue(doc, input.getName(), Blob.class));
                        props.put(input.getName(), Tensor.text(text));
                    } else {
                        String val = getPropertyValue(doc, input.getName(), String.class);
                        props.put(input.getName(), Tensor.text(val));
                    }
                    break;
                case CATEGORY_TYPE:
                    String categories = getPropertyValue(doc, input.getName(), String.class);
                    if (isNotEmpty(categories)) {
                        props.put(input.getName(), Tensor.category(categories.split(LIST_DELIMITER_PATTERN)));
                    }
                    break;
                default:
                    // default to text String
                    props.put(input.getName(), Tensor.text(getPropertyValue(doc, input.getName(), String.class)));
                }
            }

            String repoName;
            String docId;

            try {
                repoName = doc.getRepositoryName();
                docId = doc.getId();
            } catch (UnsupportedOperationException e) {
                log.debug("Unable to get the document repositoryName and id.");
                repoName = UNSET;
                docId = UNSET;
            }
            return predict(props, repoName, docId);
        } finally {
            preConversionTime.stop();
        }
    }

    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobtext) {
        Map<String, Tensor> inputProperties = new HashMap<>();
        CoreSession session = CoreInstance.getCoreSessionSystem(blobtext.getRepositoryName());
        for (Map.Entry<PropertyType, ManagedBlob> blobEntry : blobtext.computePropertyBlobs().entrySet()) {
            if (IMAGE_TYPE.equals(blobEntry.getKey().getType())) {
                ManagedBlob blob = blobEntry.getValue();
                if (blob == null) {
                    return emptyList();
                }
                Blob rendition = blob;
                try {
                    DocumentModel doc = session.getDocument(new IdRef(blobtext.getId()));
                    // Get rendition if it exists
                    rendition = getPictureConversion(doc, blob);
                } catch (NuxeoException e) {
                    log.warn("Cannot fall back on picture rendition", e);
                }
                // If Blob size is too big, just abort by returning null
                if (rendition.getLength() < Long.parseLong(
                        Framework.getProperty(AI_BLOB_MAX_SIZE_CONF_VAR, AI_BLOB_MAX_SIZE_VALUE))) {
                    inputProperties.put(blobEntry.getKey().getName(), Tensor.image(convertImageBlob(rendition)));
                } else {
                    return emptyList();
                }
            } else if (TEXT_TYPE.equals(blobEntry.getKey().getType())) {
                inputProperties.put(blobEntry.getKey().getName(), Tensor.text(convertTextBlob(blobEntry.getValue())));
            }
        }

        for (ModelProperty input : inputs) {
            String text = blobtext.getProperties().get(input.getName());
            if (text != null) {
                if (CATEGORY_TYPE.equals(input.getType())) {
                    inputProperties.put(input.getName(), Tensor.category(text.split(LIST_DELIMITER_PATTERN)));
                } else {// default to text String
                    inputProperties.put(input.getName(), Tensor.text(text));
                }
            }

        }

        if (inputProperties.isEmpty()) {
            log.warn(String.format("(%s) unable to suggest doc properties for doc %s", getName(), blobtext.getId()));
        } else {
            EnrichmentMetadata suggestion = predict(inputProperties, blobtext.getRepositoryName(), blobtext.getId());
            if (suggestion != null && !suggestion.getLabels().isEmpty()) {
                return singletonList(suggestion);
            }
        }
        return emptyList();
    }

    /**
     * A JSON representation of Tensorflow instance parameters
     */
    protected static class TensorInstances {

        public final String docId;

        public final List<Map<String, Tensor>> instances;

        public TensorInstances(@JsonProperty("docId") String docId,
                @JsonProperty("instances") Map<String, Tensor> inputs) {
            this.docId = docId;
            this.instances = Collections.singletonList(inputs);
        }
    }

    /**
     * A JSON representation of Tensorflow instance parameter
     */
    protected static class Tensor implements Serializable {
        private static final long serialVersionUID = 2603715122387085509L;

        public final String type;

        public final String b64;

        public final String text;

        public final String[] categories;

        protected Tensor(String type, String b64, String text, String[] categories) {
            this.type = type;
            this.b64 = b64;
            this.text = text;
            this.categories = categories;
        }

        public static Tensor image(String b64) {
            return new Tensor(IMAGE_TYPE, b64, null, null);
        }

        public static Tensor text(String text) {
            return new Tensor(TEXT_TYPE, null, text, null);
        }

        public static Tensor category(String[] categories) {
            return new Tensor(CATEGORY_TYPE, null, null, categories);
        }
    }
}
