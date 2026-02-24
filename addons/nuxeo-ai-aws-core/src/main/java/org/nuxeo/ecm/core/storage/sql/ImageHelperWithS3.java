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

import java.util.function.BiFunction;

import org.nuxeo.ecm.blob.s3.S3BlobProvider;
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
     * Gets the image from the S3BinaryManager as a reference to an S3 object
     */
    public Image getImage(ManagedBlob blob) {
        software.amazon.awssdk.services.rekognition.model.S3Object s3Object = getS3Object(blob,
                (provider,
                        key) -> software.amazon.awssdk.services.rekognition.model.S3Object.builder()
                                                                                          .bucket(bucketName(provider))
                                                                                          .name(key)
                                                                                          .build());
        return s3Object != null ? Image.builder().s3Object(s3Object).build() : null;
    }

    /**
     * Gets the image from the S3BinaryManager as a reference to an S3 object
     */
    public Video getVideo(ManagedBlob blob) {
        software.amazon.awssdk.services.rekognition.model.S3Object s3Object = getS3Object(blob,
                (provider,
                        key) -> software.amazon.awssdk.services.rekognition.model.S3Object.builder()
                                                                                          .bucket(bucketName(provider))
                                                                                          .name(key)
                                                                                          .build());
        return s3Object != null ? Video.builder().s3Object(s3Object).build() : null;
    }

    /**
     * Gets the image from the S3BinaryManager as a reference to an S3 object as a Document
     *
     * @since 2.1.2
     */
    public Document getDocument(ManagedBlob blob) {
        software.amazon.awssdk.services.textract.model.S3Object s3Object = getS3Object(blob,
                (provider, key) -> software.amazon.awssdk.services.textract.model.S3Object.builder()
                                                                                          .bucket(bucketName(provider))
                                                                                          .name(key)
                                                                                          .build());
        return s3Object != null ? Document.builder().s3Object(s3Object).build() : null;
    }

    /**
     * Gets the S3Object from the S3BinaryManager
     */
    public <R> R getS3Object(ManagedBlob blob, BiFunction<BlobProvider, String, R> s3ObjectSupplier) {
        BlobProvider provider = Framework.getService(BlobManager.class).getBlobProvider(blob.getProviderId());
        if (provider instanceof S3BlobProvider) {
            S3BlobProvider s3 = (S3BlobProvider) provider;
            return s3ObjectSupplier.apply(s3, s3.config.bucketPrefix + blob.getKey());
        }
        return null;
    }

    public String bucketName(BlobProvider provider) {
        if (provider instanceof S3BlobProvider) {
            return ((S3BlobProvider) provider).config.bucketName;
        }
        return null;
    }

}
