/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.sns;

import static org.junit.Assert.assertNotNull;
import static org.nuxeo.ai.rekognition.RekognitionService.DETECT_SNS_TOPIC;

import javax.inject.Inject;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.AWS;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, RuntimeFeature.class, PlatformFeature.class})
@Deploy("org.nuxeo.runtime.aws")
@Deploy("org.nuxeo.ai.aws.aws-core")
public class TestNotificationService {

    @Inject
    protected NotificationService ns;

    @AfterClass
    public static void cleanup() {
        AWS.assumeCredentials();
        Framework.getService(NotificationService.class).removeTopic(DETECT_SNS_TOPIC);
    }

    @Test
    @Deploy("org.nuxeo.ai.aws.aws-core:OSGI-INF/test-aws-sns.xml")
    public void shouldCreateTopic() {
        AWS.assumeCredentials();
        assertNotNull(ns);
        String arn = ns.getTopicArnFor(DETECT_SNS_TOPIC);
        assertNotNull(arn);
    }
}
