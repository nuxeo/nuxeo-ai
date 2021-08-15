/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.gcp.provider;

import static java.util.Collections.singleton;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.enrichment.AbstractEnrichmentProvider;
import org.nuxeo.ai.enrichment.EnrichmentDescriptor;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentUtils;
import org.nuxeo.ai.gcp.AIGoogleService;
import org.nuxeo.ai.gcp.metrics.GCPMetrics;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageContext;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;

/**
 * Abstract class representing any GCP based provider for acquiring Tags
 *
 * @param <T> which conforms to {@link MessageOrBuilder}. See {@link com.google.cloud.vision.v1.EntityAnnotation},
 *            {@link com.google.cloud.vision.v1.FaceAnnotation}, and
 *            {@link com.google.cloud.vision.v1.LocalizedObjectAnnotation}
 */
public abstract class AbstractTagProvider<T extends MessageOrBuilder> extends AbstractEnrichmentProvider {

    private static final Logger log = LogManager.getLogger(AbstractTagProvider.class);

    public static final String MINIMUM_CONFIDENCE = "minConfidence";

    public static final String DEFAULT_MAX_RESULTS = "200";

    public static final String DEFAULT_CONFIDENCE = "70";

    protected int maxResults;

    protected float minConfidence;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        Map<String, String> options = descriptor.options;
        maxResults = Integer.parseInt(options.getOrDefault(MAX_RESULTS, DEFAULT_MAX_RESULTS));
        minConfidence = Float.parseFloat(options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
        minConfidence = minConfidence > 1.0f ? minConfidence / 100.0f : minConfidence;
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument doc) {
        List<AnnotateImageRequest> requests = new ArrayList<>();
        ArrayList<ManagedBlob> managedBlobs = new ArrayList<>(doc.getBlobs().values());
        for (ManagedBlob managedBlob : managedBlobs) {
            Blob blob = EnrichmentUtils.getBlobFromProvider(managedBlob);
            ByteString imgBytes;
            try {
                imgBytes = ByteString.readFrom(blob.getStream());
            } catch (IOException e) {
                log.error(e);
                continue;
            }

            Image img = Image.newBuilder().setContent(imgBytes).build();
            // TODO: Consider chaining via `setType` overload
            Feature feat = Feature.newBuilder().setType(getType()).setMaxResults(maxResults).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                                                               .addFeatures(feat)
                                                               .setImageContext(getImageContext())
                                                               .setImage(img)
                                                               .build();
            requests.add(request);
        }

        try (ImageAnnotatorClient vision = Framework.getService(AIGoogleService.class).getOrCreateClient()) {
            BatchAnnotateImagesResponse response = vision.batchAnnotateImages(requests);
            List<AnnotateImageResponse> responses = response.getResponsesList();
            Collection<EnrichmentMetadata> result = processResult(doc, managedBlobs, responses);
            registerMetrics();
            return result;
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }

    protected void registerMetrics() {
        GCPMetrics gcpMetrics = Framework.getService(GCPMetrics.class);
        switch (getType()) {
        case FACE_DETECTION:
            gcpMetrics.getFaceCalls().inc();
            break;
        case CROP_HINTS:
            gcpMetrics.getCropHintsCalls().inc();
            break;
        case TEXT_DETECTION:
            gcpMetrics.getTextCalls().inc();
            break;
        case OBJECT_LOCALIZATION:
            gcpMetrics.getObjectLocalizationCalls().inc();
            break;
        case IMAGE_PROPERTIES:
            gcpMetrics.getImagePropertiesCalls().inc();
            break;
        case LANDMARK_DETECTION:
            gcpMetrics.getLandmarkCalls().inc();
            break;
        case LABEL_DETECTION:
            gcpMetrics.getLabelsCalls().inc();
            break;
        case LOGO_DETECTION:
            gcpMetrics.getLogoCalls().inc();
            break;
        default:
            log.warn("This type " + getType() + " is not tracked as a metric");
            break;
        }
        gcpMetrics.getVisionGlobalCalls().inc();
    }

    protected List<EnrichmentMetadata> processResult(BlobTextFromDocument doc, List<ManagedBlob> blobs,
            List<AnnotateImageResponse> responses) {
        Iterator<ManagedBlob> iterator = blobs.iterator();
        List<EnrichmentMetadata> results = new ArrayList<>();
        for (AnnotateImageResponse res : responses) {
            if (res.hasError()) {
                log.error(res.getError().getMessage());
                continue;
            }

            List<T> annotations = getAnnotationList(res);
            List<AIMetadata.Tag> labels = annotations.stream()
                                                     .map(this::newTag)
                                                     .filter(tag -> tag.confidence >= minConfidence)
                                                     .collect(Collectors.toList());

            String raw = toJsonString(jg -> jg.writeObjectField("tags", JsonFormat.printer().print(res)));
            String rawKey = saveJsonAsRawBlob(raw);

            EnrichmentMetadata build = new EnrichmentMetadata.Builder(kind, name, doc).withTags(asTags(labels))
                                                                                      .withRawKey(rawKey)
                                                                                      .withDocumentProperties(singleton(
                                                                                              iterator.next().getKey()))
                                                                                      .build();
            results.add(build);
        }

        return results;
    }

    /**
     * @return {@link com.google.cloud.vision.v1.Feature.Type} as the type of request
     */
    protected abstract Feature.Type getType();

    /**
     * @return {@link com.google.cloud.vision.v1.ImageContext} which contains parameters
     */
    protected ImageContext getImageContext() {
        return ImageContext.getDefaultInstance();
    }

    /**
     * @param response {@link AnnotateImageResponse} from GCP
     * @return {@link List} of Objects conforming to {@link MessageOrBuilder}. See this class definition
     */
    protected abstract List<T> getAnnotationList(AnnotateImageResponse response);

    /**
     * @param annotation An object conforming to {@link MessageOrBuilder}. See this class definition
     * @return {@link org.nuxeo.ai.metadata.AIMetadata.Tag}
     */
    protected abstract AIMetadata.Tag newTag(T annotation);
}
