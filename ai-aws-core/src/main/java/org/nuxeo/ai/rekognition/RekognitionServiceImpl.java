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
package org.nuxeo.ai.rekognition;

import java.util.function.BiFunction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.storage.sql.RekognitionHelperWithS3;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

import com.amazonaws.AmazonWebServiceResult;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.model.Attribute;
import com.amazonaws.services.rekognition.model.DetectFacesRequest;
import com.amazonaws.services.rekognition.model.DetectFacesResult;
import com.amazonaws.services.rekognition.model.DetectLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.DetectTextRequest;
import com.amazonaws.services.rekognition.model.DetectTextResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.RecognizeCelebritiesRequest;
import com.amazonaws.services.rekognition.model.RecognizeCelebritiesResult;

/**
 * Implementation of RekognitionService
 */
public class RekognitionServiceImpl extends DefaultComponent implements RekognitionService {

    public static final boolean USE_S3_STORAGE;
    private static final Log log = LogFactory.getLog(RekognitionServiceImpl.class);

    static {
        boolean hasS3BinaryManager = true;
        try {
            Class.forName("org.nuxeo.ecm.core.storage.sql.S3BinaryManager");
        } catch (ClassNotFoundException e) {
            hasS3BinaryManager = false;
        }
        USE_S3_STORAGE = hasS3BinaryManager;
    }

    protected RekognitionHelper rekognitionHelper;

    @Override
    public DetectLabelsResult detectLabels(ManagedBlob blob, int maxResults, float minConfidence) {
        return detectWithClient(blob, (client, image) -> {
            DetectLabelsRequest detectLabelsRequest = new DetectLabelsRequest()
                    .withMaxLabels(maxResults)
                    .withMinConfidence(minConfidence)
                    .withImage(image);
            return client.detectLabels(detectLabelsRequest);
        });
    }

    @Override
    public DetectTextResult detectText(ManagedBlob blob) {
        return detectWithClient(blob, (client, image) -> {
            DetectTextRequest request = new DetectTextRequest().withImage(image);
            return client.detectText(request);
        });
    }

    @Override
    public DetectFacesResult detectFaces(ManagedBlob blob, Attribute... attributes) {
        return detectWithClient(blob, (client, image) -> {
            DetectFacesRequest request = new DetectFacesRequest().withImage(image).withAttributes(attributes);
            return client.detectFaces(request);
        });
    }

    @Override
    public RecognizeCelebritiesResult detectCelebrityFaces(ManagedBlob blob) {
        return detectWithClient(blob, (client, image) -> {
            RecognizeCelebritiesRequest request = new RecognizeCelebritiesRequest().withImage(image);
            return client.recognizeCelebrities(request);
        });
    }

    /**
     * Sets up the Client and Image, then calls AWS using the supplied BiFunction.
     */
    protected <T extends AmazonWebServiceResult> T detectWithClient(ManagedBlob blob, BiFunction<AmazonRekognition, Image, T> func) {
        BlobProvider blobProvider = Framework.getService(BlobManager.class).getBlobProvider(blob.getProviderId());
        AmazonRekognition client = rekognitionHelper.getClient(blobProvider);
        Image image = rekognitionHelper.getImage(blobProvider, blob.getKey());
        if (image != null && client != null) {
            T result = func.apply(client, image);
            if (log.isDebugEnabled()) {
                log.debug("Result of call to AWS " + String.valueOf(result));
            }
            return result;
        }
        return null;
    }

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        rekognitionHelper = USE_S3_STORAGE ?
                new RekognitionHelperWithS3(new DefaultRekognitionHelper()) : new DefaultRekognitionHelper();
    }

}
