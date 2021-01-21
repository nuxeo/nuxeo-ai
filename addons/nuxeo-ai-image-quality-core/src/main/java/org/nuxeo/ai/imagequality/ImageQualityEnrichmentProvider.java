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
 *     jgarzon <jgarzon@nuxeo.com>
 */
package org.nuxeo.ai.imagequality;

import static java.util.Collections.emptyList;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.getBlobFromProvider;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.nuxeo.ai.enrichment.EnrichmentDescriptor;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.imagequality.metrics.SightEngineMetrics;
import org.nuxeo.ai.imagequality.pojo.Box;
import org.nuxeo.ai.imagequality.pojo.Celebrity;
import org.nuxeo.ai.imagequality.pojo.Faces;
import org.nuxeo.ai.imagequality.pojo.ImageProperties;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.rest.RestEnrichmentProvider;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;

/**
 * An implementation of an {@link org.nuxeo.ai.enrichment.EnrichmentProvider} with Sightengine.
 * <p>
 * The api call is json based, it extends RestEnrichmentProvider to benefit from the RestClient methods. Response
 * parsing is done with Jackson and the classes in the pojo directory.
 */
public class ImageQualityEnrichmentProvider extends RestEnrichmentProvider {

    /**
     * Your API secret
     */
    public static final String PARAM_API_SECRET = "api_secret";

    /**
     * Your API user
     */
    public static final String PARAM_API_USER = "api_user";

    /**
     * List of models
     * <p>
     * The models string is a comma-separated list of the models you want to apply. The string denominations for each
     * model is:
     * <ul>
     * <li><strong>nudity</strong> for the nudity detection</li>
     * <li><strong>wad</strong> for the weapons-alcohol-drugs detection</li>
     * <li><strong>properties</strong> for image properties</li>
     * <li><strong>face</strong> for face detection</li>
     * <li><strong>face-attributes</strong> for face-attributes detection</li>
     * <li><strong>celebrities</strong> for celebrity detection</li>
     * <li><strong>type</strong> for type detection</li>
     * <li><strong>scam</strong> for scammers detection</li>
     * <li><strong>text</strong> for text detection</li>
     * <li><strong>offensive</strong> for offensive detection</li>
     * </ul>
     **/
    public static final String PARAM_MODELS = "models";

    /**
     * The image (local file) to be moderated
     */
    public static final String PARAM_MEDIA = "media";

    public static final String DEFAULT_SIGHTENGINE_MODELS = "properties";

    public static final String API_KEY_CONF = "nuxeo.ai.sightengine.apiKey";

    public static final String API_SECRET_CONF = "nuxeo.ai.sightengine.apiSecret";

    public static final String MINIMUM_CONFIDENCE = "minConfidence";

    public static final String DEFAULT_CONFIDENCE = "0.7";

    private static final Log log = LogFactory.getLog(ImageQualityEnrichmentProvider.class);

    protected String apiKey;

    protected String apiSecret;

    protected String models;

    protected float minConfidence;

    protected SightEngineMetrics sightEngineMetrics;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        sightEngineMetrics = Framework.getService(SightEngineMetrics.class);
        this.apiKey = descriptor.options.get(API_KEY_CONF);
        this.apiSecret = descriptor.options.get(API_SECRET_CONF);
        if (StringUtils.isBlank(apiKey) || StringUtils.isBlank(apiSecret)) {
            throw new IllegalArgumentException(
                    String.format("%s and %s are required configuration parameters", API_KEY_CONF, API_SECRET_CONF));
        }
        this.models = descriptor.options.getOrDefault(PARAM_MODELS, DEFAULT_SIGHTENGINE_MODELS);
        minConfidence = Float.parseFloat(descriptor.options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
    }

    @Override
    public HttpUriRequest prepareRequest(RequestBuilder requestBuilder, BlobTextFromDocument blobTextFromDoc) {

        if (blobTextFromDoc.getBlobs().size() != 1) {
            throw new NuxeoException("Sightengine only supports one blob image at a time.");
        }
        File file = getBlobFromProvider(
                blobTextFromDoc.getBlobs().values().stream().findFirst().get()).getFile();

        // Use the multipart builder
        setMultipart(requestBuilder, builder -> {
            builder.addTextBody(PARAM_MODELS, models, ContentType.DEFAULT_BINARY);
            builder.addTextBody(PARAM_API_USER, apiKey, ContentType.DEFAULT_BINARY);
            builder.addTextBody(PARAM_API_SECRET, apiSecret, ContentType.DEFAULT_BINARY);
            builder.addBinaryBody(PARAM_MEDIA, file, ContentType.DEFAULT_BINARY, file.getName());
        });

        // Add request header
        requestBuilder.addHeader(HttpHeaders.CACHE_CONTROL, "no-cache");

        // Build the request
        return requestBuilder.build();
    }

    @Override
    public Collection<EnrichmentMetadata> handleResponse(HttpResponse httpResponse,
            BlobTextFromDocument blobTextFromDoc) {
        String json = getContent(httpResponse);
        try {
            if (log.isDebugEnabled()) {
                log.debug("Returned json is " + json);
            }
            String rawKey = saveJsonAsRawBlob(json);
            ImageProperties imgProperties = MAPPER.readValue(json, ImageProperties.class);
            Collection<EnrichmentMetadata> result = processResponseProperties(imgProperties, rawKey, blobTextFromDoc);
            sightEngineMetrics.getSightEngineCalls().inc();
            return result;
        } catch (IOException e) {
            log.warn("Failed to process json return from siteengine.", e);
        }
        return emptyList();
    }

    /**
     * Process the returned json image properties into enrichment metadata.
     */
    protected Collection<EnrichmentMetadata> processResponseProperties(ImageProperties props, String rawKey,
            BlobTextFromDocument blobTextFromDoc) {
        List<EnrichmentMetadata.Label> labels = new ArrayList<>();
        List<AIMetadata.Tag> tags = new ArrayList<>();

        if (props.getAlcohol() > minConfidence) {
            labels.add(new AIMetadata.Label("alcohol", props.getAlcohol()));
        }
        if (props.getDrugs() > minConfidence) {
            labels.add(new AIMetadata.Label("drugs", props.getDrugs()));
        }
        if (props.getWeapon() > minConfidence) {
            labels.add(new AIMetadata.Label("weapon", props.getWeapon()));
        }
        if (props.getScam() != null && props.getScam().getProb() > minConfidence) {
            labels.add(new AIMetadata.Label("scam", props.getScam().getProb()));
        }
        if (props.getNudity() != null) {
            if (props.getNudity().getRaw() > minConfidence) {
                labels.add(new AIMetadata.Label("nudity/raw", props.getNudity().getRaw()));
            }
            if (props.getNudity().getPartial() > minConfidence) {
                labels.add(new AIMetadata.Label("nudity/partial", props.getNudity().getPartial()));
            }
            if (props.getNudity().getSafe() > minConfidence) {
                labels.add(new AIMetadata.Label("safe", props.getNudity().getSafe()));
            }
        }

        if (props.getText() != null && props.getText().getBoxes() != null && !props.getText().getBoxes().isEmpty()) {
            tags.addAll(props.getText()
                             .getBoxes()
                             .stream()
                             .map(this::newTag)
                             .filter(Objects::nonNull)
                             .collect(Collectors.toList()));

        }

        if (props.getOffensive() != null) {
            if (props.getOffensive().getProb() > minConfidence) {
                labels.add(new AIMetadata.Label("offensive", props.getOffensive().getProb()));
            }
            if (props.getOffensive().getBoxes() != null && !props.getOffensive().getBoxes().isEmpty()) {
                tags.addAll(props.getOffensive()
                                 .getBoxes()
                                 .stream()
                                 .map(this::newTag)
                                 .filter(Objects::nonNull)
                                 .collect(Collectors.toList()));
            }
        }

        if (props.getType() != null) {
            if (props.getType().getPhoto() > minConfidence) {
                labels.add(new AIMetadata.Label("photo", props.getType().getPhoto()));
            }
            if (props.getType().getIllustration() > minConfidence) {
                labels.add(new AIMetadata.Label("illustration", props.getType().getIllustration()));
            }
        }

        if (props.getFace() != null) {
            if (props.getFace().getSingle() > minConfidence) {
                labels.add(new AIMetadata.Label("face/single", props.getFace().getSingle()));
            }
            if (props.getFace().getMultiple() > minConfidence) {
                labels.add(new AIMetadata.Label("face/multiple", props.getFace().getMultiple()));
            }
        }

        if (props.getFaces() != null && !props.getFaces().isEmpty()) {
            for (Faces faces : props.getFaces()) {
                if (faces.getCelebrity() != null && !faces.getCelebrity().isEmpty()) {
                    for (Celebrity celeb : faces.getCelebrity()) {
                        if (celeb.getProb() > minConfidence) {
                            labels.add(new AIMetadata.Label(celeb.getName(), celeb.getProb()));
                        }
                    }
                }

                if (faces.getAttributes() != null && !faces.getAttributes().isEmpty()) {
                    for (Map.Entry<String, Float> attrib : faces.getAttributes().entrySet()) {
                        labels.add(new AIMetadata.Label(attrib.getKey(), attrib.getValue()));
                    }
                }
            }
        }

        if (props.getColors() != null && props.getColors().getDominant() != null) {
            tags.add(new AIMetadata.Tag(props.getColors().getDominant().getHex(), "color/dominant", null, null, null,
                    0.1F));
        }

        // perhaps they are tags
        // "sharpness": 0.981,
        // "contrast": 0.838,
        // "brightness": 0.626,
        labels.add(new AIMetadata.Label(props.getSharpnessDescription(), props.getSharpness()));
        labels.add(new AIMetadata.Label(props.getContrastDescription(), props.getContrast()));
        labels.add(new AIMetadata.Label(props.getBrightnessDescription(), props.getBrightness()));

        // props.getMedia() and props.getRequest() are not used

        // Return the result
        return Collections.singletonList(
                new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc).withLabels(asLabels(labels))
                                                                           .withTags(asTags(tags))
                                                                           .withRawKey(rawKey)
                                                                           .build());
    }

    protected AIMetadata.Tag newTag(Box box) {
        if (box.getProb() >= minConfidence) {
            return new EnrichmentMetadata.Tag(box.getLabel(), kind, null,
                    new AIMetadata.Box(box.getX2(), box.getY2(), box.getX1(), box.getY1()), null, box.getProb());
        }
        return null;
    }

}
