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

import org.nuxeo.ecm.blob.s3.S3BlobKey;
import org.nuxeo.ecm.blob.s3.S3BlobProvider;
import org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;

import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.Video;
import software.amazon.awssdk.services.textract.model.Document;

/**
 * A Helper which takes advantage of the S3BlobProvider
 */
public class ImageHelperWithS3 {

    public ImageHelperWithS3() {
    }

    /**
     * Gets the image from the S3BlobProvider as a reference to an S3 object
     */
    public Image getImage(ManagedBlob blob) {
        S3BlobStoreConfiguration config = getConfiguration(blob);
        return config != null ? getImage(config, blob.getKey()) : null;
    }

    /**
     * Gets the image from the S3BlobProvider as a reference to an S3 object
     */
    public Video getVideo(ManagedBlob blob) {
        S3BlobStoreConfiguration config = getConfiguration(blob);
        return config != null ? getVideo(config, blob.getKey()) : null;
    }

    /**
     * Gets the image from the S3BlobProvider as a reference to an S3 object as a Document
     *
     * @since 2.1.2
     */
    public Document getDocument(ManagedBlob blob) {
        S3BlobStoreConfiguration config = getConfiguration(blob);
        return config != null ? getDocument(config, blob.getKey()) : null;
    }

    protected Image getImage(S3BlobStoreConfiguration config, String blobKey) {
        return Image.builder().s3Object(rekognitionS3Object(config, blobKey)).build();
    }

    protected Video getVideo(S3BlobStoreConfiguration config, String blobKey) {
        return Video.builder().s3Object(rekognitionS3Object(config, blobKey)).build();
    }

    protected Document getDocument(S3BlobStoreConfiguration config, String blobKey) {
        S3BlobKey s3Key = getS3Key(config, blobKey);
        return Document.builder()
                       .s3Object(software.amazon.awssdk.services.textract.model.S3Object.builder()
                                                                                        .bucket(config.bucketName)
                                                                                        .name(s3Key.bucketKey())
                                                                                        .version(s3Key.versionId())
                                                                                        .build())
                       .build();
    }

    protected software.amazon.awssdk.services.rekognition.model.S3Object rekognitionS3Object(
            S3BlobStoreConfiguration config, String blobKey) {
        S3BlobKey s3Key = getS3Key(config, blobKey);
        return software.amazon.awssdk.services.rekognition.model.S3Object.builder()
                                                                         .bucket(config.bucketName)
                                                                         .name(s3Key.bucketKey())
                                                                         .version(s3Key.versionId())
                                                                         .build();
    }

    /**
     * Gets the key of the S3 object of a blob key, like the S3BlobStore does to read a blob: without the provider
     * prefix, with the bucket prefix and sub directories, and with its version on a versioned bucket.
     */
    protected S3BlobKey getS3Key(S3BlobStoreConfiguration config, String blobKey) {
        int colon = blobKey.indexOf(':');
        return new S3BlobKey(config, colon >= 0 ? blobKey.substring(colon + 1) : blobKey);
    }

    /**
     * Gets the configuration of the S3BlobProvider of the blob
     *
     * @return the configuration, or {@code null} if the blob isn't stored with a S3BlobProvider
     */
    protected S3BlobStoreConfiguration getConfiguration(ManagedBlob blob) {
        if (blob == null || blob.getKey() == null) {
            return null;
        }
        BlobProvider provider = Framework.getService(BlobManager.class).getBlobProvider(blob.getProviderId());
        return provider instanceof S3BlobProvider ? ((S3BlobProvider) provider).config : null;
    }

}
