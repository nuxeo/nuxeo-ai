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

import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.S3Object;

/**
 * A Helper which takes advantage of the S3BinaryManager
 */
public class ImageHelperWithS3 {

    public ImageHelperWithS3() {
    }

    /**
     * Gets the S3Object
     */
    public S3Object getS3Object(S3BinaryManager s3BinaryManager, String key) {
        return new S3Object().withName(key).withBucket(s3BinaryManager.getBucketName());
    }

    /**
     * Gets the image from the S3BinaryManager as a reference to an S3 object
     */
    public Image getImage(ManagedBlob blob) {
        BlobProvider blobProvider = Framework.getService(BlobManager.class).getBlobProvider(blob.getProviderId());
        if (blobProvider instanceof S3BinaryManager) {
            S3BinaryManager s3BinaryManager = (S3BinaryManager) blobProvider;
            S3Object s3Object = getS3Object(s3BinaryManager, s3BinaryManager.getBucketPrefix() + blob.getKey());
            if (s3Object != null) {
                return new Image().withS3Object(s3Object);
            }
        } else {
            return AWSHelper.getInstance().getImageAsBytes(blob);
        }
        return null;
    }
}
