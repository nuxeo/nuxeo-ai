/*
 * (C) Copyright 2026 Hyland (http://hyland.com/) and others.
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
package org.nuxeo.ai.sns;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.aws.AWSClientFactory;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * The SNS subscription done in NotificationComponent.start() needs the AWSClientFactory to be started first, otherwise
 * the subscription fails silently and Rekognition video callbacks never reach Nuxeo.
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy("org.nuxeo.runtime.aws")
@Deploy("org.nuxeo.ai.aws.aws-core")
@Deploy("org.nuxeo.ai.aws.aws-core:OSGI-INF/test-aws-config-valid-region.xml")
@Deploy("org.nuxeo.ai.aws.aws-core:OSGI-INF/test-aws-sns.xml")
public class TestNotificationSubscribeAtStartup {

    @Test
    public void awsClientFactoryStartsBeforeNotificationComponent() {
        NotificationComponent notification = (NotificationComponent) Framework.getRuntime()
                                                                              .getComponent(
                                                                                      "org.nuxeo.ai.sns.NotificationService");
        AWSClientFactory factory = (AWSClientFactory) Framework.getRuntime()
                                                               .getComponent("org.nuxeo.ai.aws.AWSClientFactory");
        assertThat(factory.getApplicationStartedOrder()).isLessThan(notification.getApplicationStartedOrder());
    }

    @Test
    public void snsClientIsCreatedWhenSubscribingAtStartup() {
        NotificationComponent notification = (NotificationComponent) Framework.getRuntime()
                                                                              .getComponent(
                                                                                      "org.nuxeo.ai.sns.NotificationService");
        // start() subscribes to the test topic: the SNS client must have been created at that time
        assertThat(notification.amazonSNS).isNotNull();
    }
}
