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
import static org.junit.Assume.assumeTrue;

import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.sns.NotificationService;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * NXCON-300: Verifies that NotificationComponent handles topic subscription failures gracefully
 * when AWS is not configured. The test-aws-sns.xml registers a topic, triggering the subscribe()
 * path in start() which must catch the exception from the unavailable AWSClientFactory.
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy("org.nuxeo.runtime.aws")
@Deploy("org.nuxeo.ai.aws.aws-core")
@Deploy("org.nuxeo.ai.aws.aws-core:OSGI-INF/test-aws-sns.xml")
public class TestNotificationComponentGracefulDegradation {

    @BeforeClass
    public static void assumeNoAWSConfig() {
        assumeTrue("AWS_REGION is set in the environment; skipping unconfigured test",
                StringUtils.isBlank(System.getenv("AWS_REGION"))
                        && StringUtils.isBlank(System.getProperty("aws.region")));
    }

    @Inject
    protected NotificationService notificationService;

    @Test
    public void startsWithTopicsWithoutCrashingWhenAWSUnavailable() {
        assertThat(notificationService).isNotNull();
    }

    @Test
    public void topicArnIsStillRegistered() {
        assertThat(notificationService.getTopicArnFor("detect")).isNotNull();
    }
}
