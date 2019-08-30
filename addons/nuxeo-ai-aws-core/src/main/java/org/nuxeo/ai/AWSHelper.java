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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentUtils;
import org.nuxeo.ai.enrichment.FatalEnrichmentError;
import org.nuxeo.ai.pipes.services.JacksonUtil;
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
import com.amazonaws.services.rekognition.model.Video;
import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import com.amazonaws.services.textract.model.Block;
import com.amazonaws.services.textract.model.Document;
import com.amazonaws.services.textract.model.Relationship;

/**
 * Helps with S3 images and AWS credentials
 *
 * @since 11.1
 */
public class AWSHelper {

    public static final String CONFIG_NAME = "nuxeo-ai-aws";

    public static final String CONFIG_USE_S3 = "nuxeo.enrichment.aws.s3";

    public static final String S3_MANAGER_NAME = "org.nuxeo.ecm.core.storage.sql.S3BinaryManager";

    public static final String NEW_LINE = "\n";

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
    public ByteBuffer getBlobAsBytes(ManagedBlob managedBlob) {
        try {
            Blob blob = EnrichmentUtils.getBlobFromProvider(managedBlob);
            if (blob != null) {
                return ByteBuffer.wrap(blob.getByteArray());
            }
        } catch (IOException e) {
            log.error(String.format("Failed to read blob %s", managedBlob.getKey()), e);
        }
        return null;
    }

    /**
     * Gets the AWS Textract Document using a managed blob.
     * @since 2.1.2
     */
    public Document getDocument(ManagedBlob managedBlob) {
        if (s3Helper != null) {
            Document document = s3Helper.getDocument(managedBlob);
            if (document != null) {
                return document;
            }
        }
        ByteBuffer byteBuffer = getBlobAsBytes(managedBlob);
        return byteBuffer != null ? new Document().withBytes(byteBuffer) : null;
    }

    /**
     * Gets the AWS Rekognition Image using a managed blob.
     */
    public Image getImage(ManagedBlob managedBlob) {
        if (s3Helper != null) {
            Image image = s3Helper.getImage(managedBlob);
            if (image != null) {
                return image;
            }
        }
        ByteBuffer byteBuffer = getBlobAsBytes(managedBlob);
        return byteBuffer != null ? new Image().withBytes(byteBuffer) : null;
    }

    /**
     * Gets the AWS Rekognition Video using a managed blob.
     */
    public Video getVideo(ManagedBlob managedBlob) {
        if (s3Helper != null) {
            return s3Helper.getVideo(managedBlob);
        }

        return null;
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

    /**
     * Debug a block element
     * @since 2.1.2
     */
    public String debugTextractBlock(Block block) {

        StringBuilder builder = new StringBuilder("Block Id : " + block.getId() + NEW_LINE);

        if (block.getText() != null) {
            builder.append("    Detected text: " + block.getText() + NEW_LINE);
        }
        builder.append("    Type: " + block.getBlockType() + NEW_LINE);

        if (!block.getBlockType().equals("PAGE")) {
            builder.append("    Confidence: " + block.getConfidence().toString() + NEW_LINE);
        }
        if (block.getBlockType().equals("CELL")) {
            builder.append("    Cell information:" + NEW_LINE);
            builder.append("        Column: " + block.getColumnIndex() + NEW_LINE);
            builder.append("        Row: " + block.getRowIndex() + NEW_LINE);
            builder.append("        Column span: " + block.getColumnSpan() + NEW_LINE);
            builder.append("        Row span: " + block.getRowSpan() + NEW_LINE);
        }

        builder.append("    Relationships" + NEW_LINE);
        List<Relationship> relationships = block.getRelationships();
        if (relationships != null) {
            for (Relationship relationship : relationships) {
                builder.append("        Type: " + relationship.getType() + NEW_LINE);
                builder.append("        IDs: " + relationship.getIds().toString() + NEW_LINE);
            }
        } else {
            builder.append("        No related Blocks" + NEW_LINE);
        }

        builder.append("    Geometry" + NEW_LINE);
        builder.append("        Bounding Box: " + block.getGeometry().getBoundingBox().toString() + NEW_LINE);
        builder.append("        Polygon: " + block.getGeometry().getPolygon().toString() + NEW_LINE);

        List<String> entityTypes = block.getEntityTypes();
        builder.append("    Entity Types" + NEW_LINE);
        if (entityTypes != null) {
            for (String entityType : entityTypes) {
                builder.append("        Entity Type: " + entityType + NEW_LINE);
            }
        } else {
            builder.append("        No entity type" + NEW_LINE);
        }
        if (block.getPage() != null) {
            builder.append("    Page: " + block.getPage() + NEW_LINE);
        }

        return builder.toString();
    }

    /**
     * Gets Textract blocks from the enrichment metadata raw blob.
     * @since 2.1.2
     */
    public List<Block> getTextractBlocks(EnrichmentMetadata metadata) throws IOException {
        String raw = EnrichmentUtils.getRawBlob(metadata);
        if (StringUtils.isNotEmpty(raw)) {
            AnalyzeDocumentResult result = JacksonUtil.MAPPER.readValue(raw, AnalyzeDocumentResult.class);
            return result.getBlocks();
        }
        return Collections.emptyList();
    }
}
