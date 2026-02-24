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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.AWSHelper;
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
 */
public class AWSClientFactory extends DefaultComponent {

    private static final Log log = LogFactory.getLog(AWSClientFactory.class);

    // Volatile for thread-safety with double-checked locking
    private volatile ComprehendClient comprehendClient;

    private volatile RekognitionClient rekognitionClient;

    private volatile TextractClient textractClient;

    private volatile TranslateClient translateClient;

    private volatile TranscribeClient transcribeClient;

    private volatile SnsClient snsClient;

    // Common configuration
    private Region region;

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        // Initialize common configuration
        this.region = AWSHelper.getInstance().getRegion();
        log.info("AWS Client Factory started with region: " + region);
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
    }

    /**
     * Get Comprehend client with lazy initialization and thread safety
     */
    public ComprehendClient getComprehendClient() {
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
                log.debug("Closed " + serviceName + " client");
            } catch (Exception e) {
                log.warn("Error closing " + serviceName + " client", e);
            }
        }
    }
}
