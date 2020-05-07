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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.nuxeo.ai.rekognition.RekognitionService.DETECT_SNS_TOPIC;

import java.net.URI;
import java.net.URISyntaxException;

import javax.inject.Inject;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.AWS;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.GetTopicAttributesRequest;
import com.amazonaws.services.sns.model.GetTopicAttributesResult;

@RunWith(FeaturesRunner.class)
@Features({RuntimeFeature.class, PlatformFeature.class})
@Deploy("org.nuxeo.runtime.aws")
@Deploy("org.nuxeo.ai.aws.aws-core")
public class TestNotificationService {

    @Inject
    protected NotificationService ns;

    @Test
    @Ignore("Requires Role available for QA")
    @Deploy("org.nuxeo.ai.aws.aws-core:OSGI-INF/test-aws-sns.xml")
    public void shouldCreateTopic() throws URISyntaxException {
        AWS.assumeCredentials();
        assertNotNull(ns);
        String arn = ns.getTopicArnFor(DETECT_SNS_TOPIC);
        assertNotNull(arn);

        AmazonSNS client = ns.getClient();
        assertNotNull(client);

        URI uri = new URI("https://not_a_path.net");
        // should subscribe with no exceptions
        String subArn = ns.subscribe(arn, uri);
        assertThat(subArn).isNotBlank();

        GetTopicAttributesRequest topicRequest = new GetTopicAttributesRequest(arn);
        GetTopicAttributesResult result = client.getTopicAttributes(topicRequest);
        assertThat(result.getAttributes()).isNotEmpty();
    }
}
