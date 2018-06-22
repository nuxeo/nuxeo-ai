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
package org.nuxeo.ecm.core.storage.sql;

import org.nuxeo.ai.rekognition.RekognitionHelper;
import org.nuxeo.ecm.core.blob.BlobProvider;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.S3Object;

/**
 * A Rekognition Helper which takes advantage of the S3BinaryManager
 */
public class RekognitionHelperWithS3 implements RekognitionHelper {

    protected RekognitionHelper fallBackHelper;

    public RekognitionHelperWithS3(RekognitionHelper fallBackHelper) {
        this.fallBackHelper = fallBackHelper;
    }

    /**
     * Gets the S3Object
     */
    public S3Object getS3Object(S3BinaryManager s3BinaryManager, String key) {
        return new S3Object().withName(key).withBucket(s3BinaryManager.bucketName);
    }

    /**
     * Configures a client based on the provided details
     */
    protected AmazonRekognition getClient(AWSCredentialsProvider credentialsProvider, String region) {
        return AmazonRekognitionClientBuilder.standard()
                                             .withCredentials(credentialsProvider)
                                             .withRegion(region).build();
    }

    @Override
    public AmazonRekognition getClient(BlobProvider blobProvider) {
        if (blobProvider instanceof S3BinaryManager) {
            S3BinaryManager s3BinaryManager = (S3BinaryManager) blobProvider;
            return getClient(s3BinaryManager.awsCredentialsProvider, s3BinaryManager.amazonS3.getRegionName());
        }
        return fallBackHelper.getClient(blobProvider);
    }

    @Override
    public Image getImage(BlobProvider blobProvider, String blobKey) {
        if (blobProvider instanceof S3BinaryManager) {
            S3BinaryManager s3BinaryManager = (S3BinaryManager) blobProvider;
            S3Object s3Object = getS3Object(s3BinaryManager, blobKey);
            return new Image().withS3Object(s3Object);
        }
        return fallBackHelper.getImage(blobProvider, blobKey);
    }
}
