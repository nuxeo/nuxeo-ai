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

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.aws.credentials.NuxeoCredentialsProviderChain;
import org.nuxeo.aws.credentials.NuxeoRegionProviderChain;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.BlobProvider;

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.Image;

/**
 * The default RekognitionHelper
 */
public class DefaultRekognitionHelper implements RekognitionHelper {

    private static final Log log = LogFactory.getLog(DefaultRekognitionHelper.class);
    protected volatile AmazonRekognition client;

    @Override
    public AmazonRekognition getClient(BlobProvider blobProvider) {
        return getClient();
    }

    /**
     * Gets the client
     */
    protected AmazonRekognition getClient() {
        AmazonRekognition result = client;
        if (result == null) {
            synchronized (this) {
                result = client;
                if (result == null) {
                    AmazonRekognitionClientBuilder builder = AmazonRekognitionClientBuilder.standard();
                    builder.withCredentials(NuxeoCredentialsProviderChain.getInstance());
                    builder.withRegion(NuxeoRegionProviderChain.getInstance().getRegion());
                    result = client = builder.build();
                }
            }
        }
        return result;
    }

    @Override
    public Image getImage(BlobProvider blobProvider, String key) {
        BlobInfo blobInfo = new BlobInfo();
        blobInfo.key = key;
        try {
            Blob blob = blobProvider.readBlob(blobInfo);
            if (blob != null) {
                return new Image().withBytes(ByteBuffer.wrap(blob.getByteArray()));
            }
        } catch (IOException e) {
            log.error(String.format("Failed to read blob %s", key), e);
        }
        return null;
    }
}
