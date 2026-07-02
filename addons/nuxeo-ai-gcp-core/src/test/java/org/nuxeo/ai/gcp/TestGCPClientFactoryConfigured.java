/*
 * (C) Copyright 2026 Nuxeo (http://nuxeo.com/) and others.
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

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;

/**
 * NXCON-300: Verifies that GCPClientFactory works when credentials are configured.
 * Uses fake authorized_user credentials that pass parsing but won't connect to GCP.
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy("org.nuxeo.ai.gcp.gcp-core")
@WithFrameworkProperty(name = "nuxeo.ai.google.credentials",
        value = "{\"type\":\"authorized_user\",\"client_id\":\"test-id\",\"client_secret\":\"test-secret\",\"refresh_token\":\"test-token\"}")
public class TestGCPClientFactoryConfigured {

    @Inject
    protected GCPClientFactory clientFactory;

    @Test
    public void factoryIsAvailableWithCredentials() {
        assertThat(clientFactory).isNotNull();
        assertThat(clientFactory.isAvailable()).isTrue();
    }

    @Test
    public void getImageAnnotatorClientPassesAvailabilityCheck() {
        try {
            clientFactory.getImageAnnotatorClient();
        } catch (NuxeoException e) {
            assertThat(e.getMessage()).doesNotContain("not configured");
        }
    }
}
