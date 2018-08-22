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

import static org.nuxeo.runtime.stream.pipes.functions.PropertyUtils.base64EncodeBlob;
import static org.nuxeo.runtime.stream.pipes.functions.PropertyUtils.getPropertyValue;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.enrichment.EnrichmentUtils;
import org.nuxeo.ai.rest.RestClient;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * An abstract implementation of a Runtime Model
 */
public abstract class AbstractRuntimeModel implements RuntimeModel {

    public static final String LIVENESS = "liveness.";

    public static final String DEFAULT_CONFIDENCE = "0.7";

    public static final String DEFAULT_IMAGE_WIDTH = "299";

    public static final String DEFAULT_IMAGE_HEIGHT = "299";

    public static final String DEFAULT_IMAGE_DEPTH = "16";

    public static final String IMAGE = "image";

    public static final String IMAGE_TYPE = "img";

    protected static final Log log = LogFactory.getLog(AbstractRuntimeModel.class);

    protected String id;

    protected Set<ModelDescriptor.Property> inputs;

    protected Set<String> outputs;

    protected Map<String, String> defaultOptions;

    protected Map<String, String> details;

    protected float minConfidence;

    protected String transientStore;

    protected EnrichmentUtils enrichmentUtils;

    protected int imageWidth;

    protected int imageHeight;

    protected int imageDepth;

    @Override
    public void init(ModelDescriptor descriptor) {
        this.id = descriptor.id;
        this.inputs = descriptor.inputs;
        this.outputs = descriptor.outputs;
        this.defaultOptions = descriptor.defaultOptions;
        this.details = descriptor.info;
        this.enrichmentUtils = new EnrichmentUtils();
        Map<String, String> config = descriptor.configuration;
        this.minConfidence = Float.parseFloat(config.getOrDefault("minConfidence", DEFAULT_CONFIDENCE));
        this.imageWidth = Integer.parseInt(config.getOrDefault(IMAGE + "." + EnrichmentUtils.WIDTH, DEFAULT_IMAGE_WIDTH));
        this.imageHeight = Integer.parseInt(config.getOrDefault(IMAGE + "." + EnrichmentUtils.HEIGHT, DEFAULT_IMAGE_HEIGHT));
        this.imageDepth = Integer.parseInt(config.getOrDefault(IMAGE + "." + EnrichmentUtils.DEPTH, DEFAULT_IMAGE_DEPTH));
        String transientStoreName = config.get("transientStore");
        if (StringUtils.isNotBlank(transientStoreName)) {
            this.transientStore = transientStoreName;
        }
        isLive(config, LIVENESS);
    }

    @Override
    public Map<String, String> getDefaultOptions() {
        return defaultOptions;
    }

    @Override
    public Map<String, String> getDetails() {
        return details;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Set<ModelDescriptor.Property> getInputs() {
        return inputs;
    }

    @Override
    public Set<String> getOutputs() {
        return outputs;
    }

    /**
     * Save the rawJson String as a blob using the configured TransientStore for this service and returns the blob key.
     */
    public String saveJsonAsRawBlob(String rawJson) {
        if (transientStore != null && StringUtils.isNotBlank(rawJson)) {
            return enrichmentUtils.saveRawBlob(Blobs.createJSONBlob(rawJson), transientStore);
        } else {
            return null;
        }
    }

    /**
     * Get the properties from a document as a Map of xpath key, and string value.
     */
    public Map<String, Serializable> getProperties(DocumentModel doc) {
        Map<String, Serializable> props = new HashMap<>(inputs.size());
        for (ModelDescriptor.Property input : inputs) {
            switch (input.type) {
                case IMAGE_TYPE:
                    props.put(input.name, convertImageBlob(getPropertyValue(doc, input.name, Blob.class)));
                    break;
                default:
                    // default to text String
                    props.put(input.name, getPropertyValue(doc, input.name, String.class));
            }
        }
        return props;
    }

    /**
     * Takes a reference to an image blob and turns it into a format supported by the model
     */
    protected Serializable convertImageBlob(Blob sourceBlob) {
        if (sourceBlob != null) {
            Blob blob = enrichmentUtils.convertImageBlob(sourceBlob, imageWidth, imageHeight, imageDepth);
            return base64EncodeBlob(blob);
        }
        return null;
    }

    /**
     * Checks to see if the specified url is live.
     */
    public boolean isLive(Map<String, String> config, String prefix) {
        String uri = config.get(prefix + RestClient.OPTION_URI);
        if (StringUtils.isNotBlank(uri)) {
            boolean live = RestClient.isLive(config, prefix);
            if (!live) {
                log.warn(String.format("Live check failed for %s", uri));
            }
            return live;
        }
        return false;  // If you haven't specified a url then its not live.
    }

}
