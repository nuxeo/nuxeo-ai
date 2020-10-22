/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo
 */
package org.nuxeo.ai.gcp;

import static org.nuxeo.runtime.api.Framework.getProperty;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.Environment;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;

public class AIGoogleServiceImpl extends DefaultComponent implements AIGoogleService {

    private static final Logger log = LogManager.getLogger(AIGoogleServiceImpl.class);

    /**
     * <code>
     * - absolute JSON GCP credentials file path
     * - file name of credentials in `nxserver/config`
     * - if not set Nuxeo will look into 'gcp-credentials.json' file by default (located in `nxserver/config`)
     * </code>
     */
    public static final String GOOGLE_APPLICATION_CREDENTIALS = "nuxeo.gcp.credentials";

    public static final String GCP_JSON_FILE = "gcp-credentials.json";

    public static final String GOOGLE_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        this.getOrCreateClient();
    }

    @Override
    public ImageAnnotatorClient getOrCreateClient() {
        try {
            // File googleCredentials = new File(getProperty(GOOGLE_APPLICATION_CREDENTIALS));
            File googleCredentials = new File("/Users/vp/Desktop/credentials.json");
            String credentialsPath = googleCredentials.isFile() ? googleCredentials.getAbsolutePath()
                    : new File(Environment.getDefault().getConfig(),
                            getProperty(GOOGLE_APPLICATION_CREDENTIALS, GCP_JSON_FILE)).getAbsolutePath();

            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(Files.readAllBytes(Paths.get(credentialsPath))))
                                                             .createScoped(GOOGLE_PLATFORM_SCOPE);
            credentials.refreshIfExpired();

            ImageAnnotatorSettings imageAnnotatorSettings = ImageAnnotatorSettings.newBuilder()
                                                                                  .setCredentialsProvider(
                                                                                          FixedCredentialsProvider.create(
                                                                                                  credentials))
                                                                                  .build();
            return ImageAnnotatorClient.create(imageAnnotatorSettings);
        } catch (Exception e) {
            throw new NuxeoException(e);
        }
    }
}
