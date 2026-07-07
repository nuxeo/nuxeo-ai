/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.ai.aws;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.translate.TranslateClient;

/**
 * Centralized AWS Client Factory - Single point of AWS SDK dependency management. This factory isolates all AWS SDK
 * client creation and configuration logic. Benefits: - All AWS SDK dependencies are managed in one place - Easy to
 * upgrade AWS SDK versions - Consistent client configuration across all services - Easy to add new AWS services
 * <p>
 * The factory degrades gracefully when AWS is not configured (no region). It will start without error and only fail when
 * a caller actually tries to obtain an AWS client.
 */
public class AWSClientFactory extends DefaultComponent {

    private static final Logger log = LogManager.getLogger(AWSClientFactory.class);

    // Volatile for thread-safety with double-checked locking
    private volatile ComprehendClient comprehendClient;

    private volatile RekognitionClient rekognitionClient;

    private volatile TextractClient textractClient;

    private volatile TranslateClient translateClient;

    private volatile TranscribeClient transcribeClient;

    private volatile SnsClient snsClient;

    private volatile boolean available;

    // Common configuration - resolved lazily or during start if AWS is configured
    private Region region;

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        try {
            this.region = AWSHelper.getInstance().getRegion();
            this.available = true;
            log.info("AWS Client Factory started with region: {}", region);
        } catch (Exception e) {
            this.available = false;
            log.info("AWS Client Factory started in degraded mode (AWS not configured). "
                    + "AWS AI services will not be available until the AWS region is configured. "
                    + "Cause: {}", e.getMessage());
            log.debug("AWS region resolution failure details", e);
        }
    }

    /**
     * @return {@code true} if the AWS region could be resolved and clients can be created
     */
    public boolean isAvailable() {
        return available;
    }

    private void checkAvailable() {
        if (!available) {
            throw new NuxeoException(
                    "AWS AI services are not configured. "
                            + "Please set the AWS region via the AWS_REGION environment variable, "
                            + "the aws.region system property, or nuxeo.aws.region in nuxeo.conf, "
                            + "and ensure valid AWS credentials are available "
                            + "before using AWS AI features.");
        }
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        // Clean up all clients
        closeClient(comprehendClient, "Comprehend");
        closeClient(rekognitionClient, "Rekognition");
        closeClient(textractClient, "Textract");
        closeClient(translateClient, "Translate");
        closeClient(transcribeClient, "Transcribe");
        closeClient(snsClient, "SNS");

        // Reset references
        comprehendClient = null;
        rekognitionClient = null;
        textractClient = null;
        translateClient = null;
        transcribeClient = null;
        snsClient = null;
        available = false;
    }

    /**
     * Get Comprehend client with lazy initialization and thread safety
     */
    public ComprehendClient getComprehendClient() {
        checkAvailable();
        if (comprehendClient == null) {
            synchronized (this) {
                if (comprehendClient == null) {
                    comprehendClient = ComprehendClient.builder()
                                                       .region(region)
                                                       .credentialsProvider(
                                                               AWSHelper.getInstance().getCredentialsProvider())
                                                       .build();
                    log.debug("Created new ComprehendClient");
                }
            }
        }
        return comprehendClient;
    }

    /**
     * Get Rekognition client with lazy initialization and thread safety
     */
    public RekognitionClient getRekognitionClient() {
        checkAvailable();
        if (rekognitionClient == null) {
            synchronized (this) {
                if (rekognitionClient == null) {
                    rekognitionClient = RekognitionClient.builder()
                                                         .region(region)
                                                         .credentialsProvider(
                                                                 AWSHelper.getInstance().getCredentialsProvider())
                                                         .build();
                    log.debug("Created new RekognitionClient");
                }
            }
        }
        return rekognitionClient;
    }

    /**
     * Get Textract client with lazy initialization and thread safety
     */
    public TextractClient getTextractClient() {
        checkAvailable();
        if (textractClient == null) {
            synchronized (this) {
                if (textractClient == null) {
                    textractClient = TextractClient.builder()
                                                   .region(region)
                                                   .credentialsProvider(
                                                           AWSHelper.getInstance().getCredentialsProvider())
                                                   .build();
                    log.debug("Created new TextractClient");
                }
            }
        }
        return textractClient;
    }

    /**
     * Get Translate client with lazy initialization and thread safety
     */
    public TranslateClient getTranslateClient() {
        checkAvailable();
        if (translateClient == null) {
            synchronized (this) {
                if (translateClient == null) {
                    translateClient = TranslateClient.builder()
                                                     .region(region)
                                                     .credentialsProvider(
                                                             AWSHelper.getInstance().getCredentialsProvider())
                                                     .build();
                    log.debug("Created new TranslateClient");
                }
            }
        }
        return translateClient;
    }

    /**
     * Get Transcribe client with lazy initialization and thread safety
     */
    public TranscribeClient getTranscribeClient() {
        checkAvailable();
        if (transcribeClient == null) {
            synchronized (this) {
                if (transcribeClient == null) {
                    transcribeClient = TranscribeClient.builder()
                                                       .region(region)
                                                       .credentialsProvider(
                                                               AWSHelper.getInstance().getCredentialsProvider())
                                                       .build();
                    log.debug("Created new TranscribeClient");
                }
            }
        }
        return transcribeClient;
    }

    /**
     * Get SNS client with lazy initialization and thread safety
     */
    public SnsClient getSnsClient() {
        checkAvailable();
        if (snsClient == null) {
            synchronized (this) {
                if (snsClient == null) {
                    snsClient = SnsClient.builder()
                                         .region(region)
                                         .credentialsProvider(AWSHelper.getInstance().getCredentialsProvider())
                                         .build();
                    log.debug("Created new SnsClient");
                }
            }
        }
        return snsClient;
    }

    /**
     * Helper method to safely close AWS clients
     */
    private void closeClient(AutoCloseable client, String serviceName) {
        if (client != null) {
            try {
                client.close();
                log.debug("Closed {} client", serviceName);
            } catch (Exception e) {
                log.warn("Error closing {} client", serviceName, e);
            }
        }
    }
}
