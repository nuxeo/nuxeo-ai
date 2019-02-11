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

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.CONVERSION_SERVICE;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.DEFAULT_CONVERTER;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.optionAsInteger;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.CATEGORY_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.IMAGE_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.base64EncodeBlob;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.getPropertyValue;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.enrichment.EnrichmentUtils;
import org.nuxeo.ai.model.ModelProperty;
import org.nuxeo.ai.rest.RestClient;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.convert.api.ConverterNotRegistered;
import org.nuxeo.ecm.platform.picture.api.ImagingConvertConstants;
import org.nuxeo.runtime.api.Framework;

/**
 * An abstract implementation of a Runtime Model
 */
public abstract class AbstractRuntimeModel implements RuntimeModel {

    public static final String LIVENESS = "liveness.";

    public static final String DEFAULT_CONFIDENCE = "0.7";

    protected static final Log log = LogFactory.getLog(AbstractRuntimeModel.class);

    protected String id;

    protected Set<ModelProperty> inputs;

    protected Set<ModelProperty> outputs;

    protected Map<String, String> info;

    protected float minConfidence;

    protected String transientStore;

    protected String conversionService;

    protected int imageWidth;

    protected int imageHeight;

    protected int imageDepth;

    protected String imageFormat;

    @Override
    public void init(ModelDescriptor descriptor) {
        this.id = descriptor.id;
        this.inputs = descriptor.getInputs();
        this.outputs = descriptor.getOutputs();
        this.info = descriptor.info;
        if (isBlank(getName())) {
            throw new IllegalArgumentException(MODEL_NAME + " is a mandatory info parameter");
        }
        Map<String, String> config = descriptor.configuration;
        this.minConfidence = Float.parseFloat(config.getOrDefault("minConfidence", DEFAULT_CONFIDENCE));
        this.conversionService = config.getOrDefault(CONVERSION_SERVICE, DEFAULT_CONVERTER);
        try {
            Framework.getService(ConversionService.class).isConverterAvailable(conversionService);
        } catch (ConverterNotRegistered e) {
            log.warn(conversionService + " converter is not registered.  You will not be able to convert images.");
        }
        this.imageWidth = optionAsInteger(config, ImagingConvertConstants.OPTION_RESIZE_WIDTH, EnrichmentUtils.DEFAULT_IMAGE_WIDTH);
        this.imageHeight = optionAsInteger(config, ImagingConvertConstants.OPTION_RESIZE_HEIGHT, EnrichmentUtils.DEFAULT_IMAGE_HEIGHT);
        this.imageDepth = optionAsInteger(config, ImagingConvertConstants.OPTION_RESIZE_DEPTH, EnrichmentUtils.DEFAULT_IMAGE_DEPTH);
        this.imageFormat = config
                .getOrDefault(ImagingConvertConstants.CONVERSION_FORMAT, EnrichmentUtils.DEFAULT_CONVERSATION_FORMAT);
        String transientStoreName = config.get("transientStore");
        if (StringUtils.isNotBlank(transientStoreName)) {
            this.transientStore = transientStoreName;
        }
        isLive(config, LIVENESS);
    }

    @Override
    public String getId() {
        return id;
    }

    public Map<String, String> getInfo() {
        return info;
    }

    @Override
    public Set<ModelProperty> getInputs() {
        return inputs;
    }

    @Override
    public Set<ModelProperty> getOutputs() {
        return outputs;
    }

    @Override
    public String getName() {
        return info.get(MODEL_NAME);
    }

    @Override
    public String getVersion() {
        return info.get(MODEL_VERSION);
    }

    /**
     * Save the rawJson String as a blob using the configured TransientStore for this service and returns the blob key.
     */
    public String saveJsonAsRawBlob(String rawJson) {
        if (transientStore != null && StringUtils.isNotBlank(rawJson)) {
            return EnrichmentUtils.saveRawBlob(Blobs.createJSONBlob(rawJson), transientStore);
        } else {
            return null;
        }
    }

    /**
     * Get the properties from a document as a Map of xpath key, and string value.
     */
    public Map<String, Serializable> getProperties(DocumentModel doc) {
        Map<String, Serializable> props = new HashMap<>(inputs.size());
        for (ModelProperty input : inputs) {
            switch (input.getType()) {
                case IMAGE_TYPE:
                    props.put(input.getName(), convertImageBlob(getPropertyValue(doc, input.getName(), Blob.class)));
                    break;
                case CATEGORY_TYPE:
                    Object[] categories = getPropertyValue(doc, input.getName(), Object[].class);
                    props.put(input.getName(), categories);
                    break;
                default:
                    // default to text String
                    props.put(input.getName(), getPropertyValue(doc, input.getName(), String.class));
            }
        }
        return props;
    }

    /**
     * Takes a reference to an image blob and turns it into a format supported by the model
     */
    protected Serializable convertImageBlob(Blob sourceBlob) {
        if (sourceBlob != null) {
            Blob blob = EnrichmentUtils
                    .convertImageBlob(conversionService, sourceBlob, imageWidth, imageHeight, imageDepth, imageFormat);
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
