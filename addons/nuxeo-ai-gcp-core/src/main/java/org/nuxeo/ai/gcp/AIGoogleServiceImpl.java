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

import java.io.ByteArrayInputStream;
import java.io.File;
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

    public static final String GOOGLE_CREDENTIALS_PATH_ENV = "GOOGLE_CREDENTIALS_PATH";

    public static final String GCP_JSON_FILE = "gcp-credentials.json";

    public static final String GOOGLE_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    protected ImageAnnotatorSettings settings;

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        // this.getOrCreateClient();
    }

    @Override
    public ImageAnnotatorClient getOrCreateClient() {
        if (settings == null) {
            synchronized (this) {
                if (settings == null) {
                    settings = createSettings();
                }
            }
        }

        try {
            // TODO: a better refresh mechanic should be introduced
            settings.getCredentialsProvider().getCredentials().refresh();
            return ImageAnnotatorClient.create(settings);
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }

    protected ImageAnnotatorSettings createSettings() {
        String credentialPath = Framework.getProperty(GOOGLE_APPLICATION_CREDENTIALS);
        if (StringUtils.isBlank(credentialPath)) {
            credentialPath = System.getenv(GOOGLE_CREDENTIALS_PATH_ENV);
        }

        File googleCredentials = new File(credentialPath);
        Path credentialsPath;
        if (googleCredentials.isFile()) {
            credentialsPath = googleCredentials.toPath();
        } else {
            credentialsPath = Paths.get(Environment.getDefault().getConfig().getAbsolutePath(),
                    Framework.getProperty(GOOGLE_APPLICATION_CREDENTIALS, GCP_JSON_FILE));
        }

        try (InputStream is = new ByteArrayInputStream(Files.readAllBytes(credentialsPath))) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(is).createScoped(GOOGLE_PLATFORM_SCOPE);
            credentials.refreshIfExpired();

            FixedCredentialsProvider provider = FixedCredentialsProvider.create(credentials);
            return ImageAnnotatorSettings.newBuilder().setCredentialsProvider(provider).build();
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }
}
