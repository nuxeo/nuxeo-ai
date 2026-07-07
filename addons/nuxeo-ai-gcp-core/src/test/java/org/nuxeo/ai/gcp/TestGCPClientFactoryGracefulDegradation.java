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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assume.assumeTrue;

import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * Verifies that GCPClientFactory degrades gracefully when GCP credentials are not configured.
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy("org.nuxeo.ai.gcp.gcp-core")
public class TestGCPClientFactoryGracefulDegradation {

    @BeforeClass
    public static void assumeNoGCPConfig() {
        assumeTrue("GOOGLE_APPLICATION_CREDENTIALS is set in the environment; skipping unconfigured test",
                StringUtils.isBlank(System.getenv("GOOGLE_APPLICATION_CREDENTIALS")));
    }

    @Inject
    protected GCPClientFactory clientFactory;

    @Test
    public void factoryStartsSuccessfullyWithoutGCPConfig() {
        assertThat(clientFactory).isNotNull();
    }

    @Test
    public void factoryReportsUnavailableWithoutGCPConfig() {
        assertThat(clientFactory.isAvailable()).isFalse();
    }

    @Test
    public void getImageAnnotatorClientThrowsWhenUnavailable() {
        assertThatThrownBy(() -> clientFactory.getImageAnnotatorClient())
                .isInstanceOf(NuxeoException.class)
                .hasMessageContaining("GCP AI services are not configured");
    }
}
