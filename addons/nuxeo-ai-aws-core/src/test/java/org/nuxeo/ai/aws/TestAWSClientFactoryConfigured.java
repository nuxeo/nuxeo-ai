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

import jakarta.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.sns.NotificationService;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * Verifies that AWSClientFactory reports available when AWS credentials/region are configured.
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy("org.nuxeo.runtime.aws")
@Deploy("org.nuxeo.ai.aws.aws-core")
@Deploy("org.nuxeo.ai.aws.aws-core:OSGI-INF/test-aws-config-valid-region.xml")
public class TestAWSClientFactoryConfigured {

    @Inject
    protected AWSClientFactory clientFactory;

    @Inject
    protected NotificationService notificationService;

    @Test
    public void factoryIsAvailableWithConfig() {
        assertThat(clientFactory).isNotNull();
        assertThat(clientFactory.isAvailable()).isTrue();
    }

    @Test
    public void canCreateAllClientsWhenConfigured() {
        assertThat(clientFactory.getComprehendClient()).isNotNull();
        assertThat(clientFactory.getRekognitionClient()).isNotNull();
        assertThat(clientFactory.getTextractClient()).isNotNull();
        assertThat(clientFactory.getTranslateClient()).isNotNull();
        assertThat(clientFactory.getTranscribeClient()).isNotNull();
        assertThat(clientFactory.getSnsClient()).isNotNull();
    }

    @Test
    public void notificationServiceCanGetClientWhenConfigured() {
        assertThat(notificationService.getClient()).isNotNull();
    }
}
