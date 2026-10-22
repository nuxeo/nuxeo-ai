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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.blob.s3.S3BlobProvider;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.Video;
import com.amazonaws.services.textract.model.Document;

/**
 * A Helper which takes advantage of the S3BinaryManager
 */
public class ImageHelperWithS3 {

    private static final Logger log = LogManager.getLogger(ImageHelperWithS3.class);

    public ImageHelperWithS3() {
    }

    /**
     * Gets the image from the S3BinaryManager as a reference to an S3 object
     */
    public Image getImage(ManagedBlob blob) {
        com.amazonaws.services.rekognition.model.S3Object s3Object = getS3Object(blob,
                (blobProvider, key) -> new com.amazonaws.services.rekognition.model.S3Object().withName(key)
                                                                                              .withBucket(bucketName(
                                                                                                      blobProvider)));
        if (s3Object != null) {
            return new Image().withS3Object(s3Object);
        }
        return null;
    }

    /**
     * Gets the image from the S3BinaryManager as a reference to an S3 object
     */
    public Video getVideo(ManagedBlob blob) {
        com.amazonaws.services.rekognition.model.S3Object s3Object = getS3Object(blob,
                (blobProvider, key) -> new com.amazonaws.services.rekognition.model.S3Object().withName(key)
                                                                                              .withBucket(bucketName(
                                                                                                      blobProvider)));
        if (s3Object != null) {
            return new Video().withS3Object(s3Object);
        }
        return null;
    }

    /**
     * Gets the image from the S3BinaryManager as a reference to an S3 object as a Document
     *
     * @since 2.1.2
     */
    public Document getDocument(ManagedBlob blob) {
        com.amazonaws.services.textract.model.S3Object s3Object = getS3Object(blob,
                (blobProvider, key) -> new com.amazonaws.services.textract.model.S3Object().withName(key)
                                                                                           .withBucket(bucketName(
                                                                                                   blobProvider)));
        if (s3Object != null) {
            return new Document().withS3Object(s3Object);
        }
        return null;
    }

    /**
     * Gets the S3Object from the S3BinaryManager
     */
    public <R> R getS3Object(ManagedBlob blob, BiFunction<BlobProvider, String, R> s3ObjectSupplier) {
        BlobManager blobManager = Framework.getService(BlobManager.class);
        BlobProvider blobProvider = blobManager.getBlobProvider(blob.getProviderId());
        // NXCON-375 diagnostics: temporary, for preview environment investigation
        log.warn("[NXCON-375] getS3Object blobClass={} providerId={} key={} digest={} resolvedProvider={}",
                blob.getClass().getName(), blob.getProviderId(), blob.getKey(), blob.getDigest(),
                blobProvider == null ? null : blobProvider.getClass().getName());
        if (blobProvider instanceof S3BinaryManager) {
            S3BinaryManager s3BinaryManager = (S3BinaryManager) blobProvider;
            log.warn("[NXCON-375] matched S3BinaryManager bucket={} prefix={}", s3BinaryManager.bucketName,
                    s3BinaryManager.getBucketPrefix());
            return s3ObjectSupplier.apply(s3BinaryManager, s3BinaryManager.getBucketPrefix() + blob.getKey());
        } else if (blobProvider instanceof S3BlobProvider) {
            S3BlobProvider provider = (S3BlobProvider) blobProvider;
            log.warn("[NXCON-375] matched S3BlobProvider bucket={} prefix={}", provider.config.bucketName,
                    provider.config.bucketPrefix);
            return s3ObjectSupplier.apply(provider, provider.config.bucketPrefix + blob.getKey());
        }

        log.warn("[NXCON-375] no S3 provider matched for providerId={}; registered providers:", blob.getProviderId());
        blobManager.getBlobProviders()
                   .forEach((id, p) -> log.warn("[NXCON-375]   provider id={} class={} superclass={}", id,
                           p.getClass().getName(), p.getClass().getSuperclass().getName()));
        return null;
    }

    public String bucketName(BlobProvider provider) {
        if (provider instanceof S3BinaryManager) {
            return ((S3BinaryManager) provider).bucketName;
        } else if (provider instanceof S3BlobProvider) {
            return ((S3BlobProvider) provider).config.bucketName;

        }

        return null;
    }

}
