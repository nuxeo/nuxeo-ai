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
package org.nuxeo.ai.gcp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.Environment;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;

/**
 * Centralized GCP Client Factory - Single point of GCP SDK dependency management.
 * This factory isolates all GCP SDK client creation and configuration logic.
 *
 * Benefits:
 * - All GCP SDK dependencies are managed in one place
 * - Easy to upgrade GCP SDK versions
 * - Consistent client configuration across all services
 * - Easy to add new GCP services
 */
public class GCPClientFactory extends DefaultComponent {

    private static final Logger log = LogManager.getLogger(GCPClientFactory.class);

    public static final String GOOGLE_CREDENTIALS_CONFIG = "nuxeo.ai.google.credentials";
    public static final String DEFAULT_CREDENTIALS_FILE = "gcp-credentials.json";

    // Volatile for thread-safety with double-checked locking
    private volatile ImageAnnotatorClient imageAnnotatorClient;

    // Common configuration
    private GoogleCredentials credentials;

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        // Initialize common configuration
        this.credentials = initializeCredentials();
        log.info("GCP Client Factory started");
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        // Clean up all clients
        closeClient(imageAnnotatorClient, "ImageAnnotator");

        // Reset references
        imageAnnotatorClient = null;
    }

    /**
     * Get ImageAnnotator client with lazy initialization and thread safety
     */
    public ImageAnnotatorClient getImageAnnotatorClient() {
        if (imageAnnotatorClient == null) {
            synchronized (this) {
                if (imageAnnotatorClient == null) {
                    try {
                        ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                                .build();
                        imageAnnotatorClient = ImageAnnotatorClient.create(settings);
                        log.debug("Created new ImageAnnotatorClient");
                    } catch (IOException e) {
                        throw new NuxeoException("Failed to create ImageAnnotatorClient", e);
                    }
                }
            }
        }
        return imageAnnotatorClient;
    }

    /**
     * Initialize Google Cloud credentials
     */
    private GoogleCredentials initializeCredentials() {
        try {
            String credentialsConfig = Framework.getProperty(GOOGLE_CREDENTIALS_CONFIG);

            if (StringUtils.isNotEmpty(credentialsConfig)) {
                // Try absolute path first
                Path credentialsPath = Paths.get(credentialsConfig);
                if (!Files.exists(credentialsPath)) {
                    // Try relative to nxserver/config
                    credentialsPath = Paths.get(Environment.getDefault().getConfig().getPath(), credentialsConfig);
                }

                if (Files.exists(credentialsPath)) {
                    try (InputStream is = Files.newInputStream(credentialsPath)) {
                        return GoogleCredentials.fromStream(is);
                    }
                } else {
                    // Try as JSON content directly
                    try (InputStream is = new ByteArrayInputStream(credentialsConfig.getBytes())) {
                        return GoogleCredentials.fromStream(is);
                    }
                }
            } else {
                // Use default credentials file
                Path defaultPath = Paths.get(Environment.getDefault().getConfig().getPath(), DEFAULT_CREDENTIALS_FILE);
                if (Files.exists(defaultPath)) {
                    try (InputStream is = Files.newInputStream(defaultPath)) {
                        return GoogleCredentials.fromStream(is);
                    }
                }
            }

            // Fallback to application default credentials
            return GoogleCredentials.getApplicationDefault();

        } catch (IOException e) {
            throw new NuxeoException("Failed to initialize Google Cloud credentials", e);
        }
    }

    /**
     * Helper method to safely close GCP clients
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
