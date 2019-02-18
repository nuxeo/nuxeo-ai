/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.enrichment.EnrichmentUtils;
import org.nuxeo.ai.enrichment.FatalEnrichmentError;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.storage.sql.ImageHelperWithS3;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.aws.NuxeoAWSCredentialsProvider;
import org.nuxeo.runtime.aws.NuxeoAWSRegionProvider;
import org.nuxeo.runtime.services.config.ConfigurationService;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.logs.model.UnrecognizedClientException;
import com.amazonaws.services.rekognition.model.AccessDeniedException;
import com.amazonaws.services.rekognition.model.Image;

/**
 * Helps with S3 images and AWS credentials
 *
 * @since 11.1
 */
public class AWSHelper {


    public static final String CONFIG_NAME = "nuxeo-ai-aws";

    public static final String CONFIG_USE_S3 = "nuxeo.enrichment.aws.s3";

    public static final String S3_MANAGER_NAME = "org.nuxeo.ecm.core.storage.sql.S3BinaryManager";

    protected static final Set<String> FATAL_ERRORS = new HashSet<>(Arrays.asList(
            UnrecognizedClientException.class.getSimpleName(),
            AccessDeniedException.class.getSimpleName()));

    private static final Logger log = LogManager.getLogger(AWSHelper.class);

    protected static AWSHelper INSTANCE;

    protected final NuxeoAWSRegionProvider regionProvider;

    protected final NuxeoAWSCredentialsProvider credentialsProvider;

    protected final ImageHelperWithS3 s3Helper;

    protected AWSHelper() {
        this.regionProvider = new NuxeoAWSRegionProvider(CONFIG_NAME);
        this.credentialsProvider = new NuxeoAWSCredentialsProvider(CONFIG_NAME);

        ImageHelperWithS3 imageHelperWithS3;
        try {
            Class.forName(S3_MANAGER_NAME);
            imageHelperWithS3 = Framework.getService(ConfigurationService.class)
                                         .isBooleanPropertyFalse(CONFIG_USE_S3) ? null : new ImageHelperWithS3();
        } catch (ClassNotFoundException e) {
            imageHelperWithS3 = null;
        }
        this.s3Helper = imageHelperWithS3;
    }

    /**
     * Gets a reference to a single instance of the AWSHelper class.
     */
    public static AWSHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AWSHelper();
        }
        return INSTANCE;
    }

    /**
     * A wrapper for enrichment that handles errors, deciding if they are fatal or not.
     */
    public static <T> T handlingExceptions(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (AmazonServiceException e) {
            throw isFatal(e) ? new FatalEnrichmentError(e) : e;
        } catch (IllegalArgumentException e) {
            throw new FatalEnrichmentError(e);
        }
    }

    /**
     * Is this exception unrecoverable?
     */
    public static boolean isFatal(AmazonServiceException e) {
        return FATAL_ERRORS.contains(e.getErrorCode());
    }

    /**
     * Gets the image bytes.
     */
    public Image getImageAsBytes(ManagedBlob managedBlob) {
        try {
            Blob blob = EnrichmentUtils.getBlobFromProvider(managedBlob);
            if (blob != null) {
                return new Image().withBytes(ByteBuffer.wrap(blob.getByteArray()));
            }
        } catch (IOException e) {
            log.error(String.format("Failed to read blob %s", managedBlob.getKey()), e);
        }
        return null;
    }

    /**
     * Gets the AWS Image using a managed blob.
     */
    public Image getImage(ManagedBlob managedBlob) {
        if (s3Helper != null) {
            return s3Helper.getImage(managedBlob);
        }
        return getImageAsBytes(managedBlob);
    }

    /**
     * Get the AWS region.
     */
    public String getRegion() {
        return regionProvider.getRegion();
    }

    /**
     * Get the AWS Credentials Provider.
     */
    public AWSCredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }
}
