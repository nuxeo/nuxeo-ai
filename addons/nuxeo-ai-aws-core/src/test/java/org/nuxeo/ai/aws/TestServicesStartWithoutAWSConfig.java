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
package org.nuxeo.ai.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assume.assumeTrue;

import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ai.sns.NotificationService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * NXCON-300: Verifies that all AWS service components start successfully when AWS is NOT configured.
 * <p>
 * Before the fix, deploying the bundle without AWS region/credentials caused a hard crash in
 * {@code AWSClientFactory.start()}, which cascaded to RekognitionService and other dependents.
 * After the fix, all components start gracefully and only fail when a client is actually requested.
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy("org.nuxeo.runtime.aws")
@Deploy("org.nuxeo.ai.aws.aws-core")
public class TestServicesStartWithoutAWSConfig {

    @BeforeClass
    public static void assumeNoAWSConfig() {
        assumeTrue("AWS_REGION is set in the environment; skipping unconfigured test",
                StringUtils.isBlank(System.getenv("AWS_REGION"))
                        && StringUtils.isBlank(System.getProperty("aws.region")));
    }

    @Inject
    protected AWSClientFactory clientFactory;

    @Inject
    protected RekognitionService rekognitionService;

    @Inject
    protected NotificationService notificationService;

    @Test
    public void allServicesStartWithoutCrash() {
        assertThat(clientFactory).as("AWSClientFactory should be registered").isNotNull();
        assertThat(rekognitionService).as("RekognitionService should be registered").isNotNull();
        assertThat(notificationService).as("NotificationService should be registered").isNotNull();
    }

    @Test
    public void clientFactoryReportsUnavailable() {
        assertThat(clientFactory.isAvailable()).isFalse();
    }

    @Test
    public void rekognitionGetClientThrowsWhenUnavailable() {
        assertThatThrownBy(() -> rekognitionService.getClient())
                .isInstanceOf(NuxeoException.class)
                .hasMessageContaining("AWS AI services are not configured");
    }

    @Test
    public void notificationGetClientThrowsWhenUnavailable() {
        assertThatThrownBy(() -> notificationService.getClient())
                .isInstanceOf(NuxeoException.class)
                .hasMessageContaining("AWS AI services are not configured");
    }
}
