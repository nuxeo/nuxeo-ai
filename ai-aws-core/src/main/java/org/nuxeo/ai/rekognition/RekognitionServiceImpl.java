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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.storage.sql.RekognitionHelperWithS3;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.model.DetectLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.DetectTextRequest;
import com.amazonaws.services.rekognition.model.DetectTextResult;
import com.amazonaws.services.rekognition.model.Image;

/**
 * Implementation of RekognitionService
 */
public class RekognitionServiceImpl extends DefaultComponent implements RekognitionService {

    private static final Log log = LogFactory.getLog(RekognitionServiceImpl.class);

    protected RekognitionHelper rekognitionHelper;

    @Override
    public DetectLabelsResult detectLabels(ManagedBlob blob, int maxResults, float minConfidence) {

        BlobProvider blobProvider = Framework.getService(BlobManager.class).getBlobProvider(blob.getProviderId());
        AmazonRekognition client = rekognitionHelper.getClient(blobProvider);
        Image image = rekognitionHelper.getImage(blobProvider, blob.getKey());
        if (image != null && client != null) {
            DetectLabelsRequest detectLabelsRequest = new DetectLabelsRequest()
                    .withMaxLabels(maxResults)
                    .withMinConfidence(minConfidence)
                    .withImage(image);

            DetectLabelsResult detectLabelsResult = client.detectLabels(detectLabelsRequest);
            if (log.isDebugEnabled()) {
                log.debug("Labels ResultStatus is " + detectLabelsResult.toString());
            }
            return detectLabelsResult;
        }

        return null;
    }

    @Override
    public DetectTextResult detectText(ManagedBlob blob) {

        BlobProvider blobProvider = Framework.getService(BlobManager.class).getBlobProvider(blob.getProviderId());
        AmazonRekognition client = rekognitionHelper.getClient(blobProvider);
        Image image = rekognitionHelper.getImage(blobProvider, blob.getKey());
        if (image != null && client != null) {

            DetectTextRequest request = new DetectTextRequest().withImage(image);

            DetectTextResult detectTextResult = client.detectText(request);
            if (log.isDebugEnabled()) {
                log.debug("Detect Text ResultStatus is " + detectTextResult.toString());
            }
            return detectTextResult;
        }
        return null;
    }

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        rekognitionHelper = new RekognitionHelperWithS3(new DefaultRekognitionHelper());
    }

}
