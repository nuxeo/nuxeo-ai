/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Gethin James
 */
package org.nuxeo.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({PlatformFeature.class})
@Deploy("org.nuxeo.ai.aws.aws-core")
@Deploy("org.nuxeo.runtime.aws")
@Deploy("org.nuxeo.ai.aws.aws-core:OSGI-INF/test-aws-config.xml")
public class TestAWSHelper {

    @Test
    public void testCustomCredentials() {
        assertEquals("MY_REGION", AWSHelper.getInstance().getRegion());
        assertEquals("MY_XML_ACCESS_KEY_ID",
                     AWSHelper.getInstance().getCredentialsProvider().getCredentials().getAWSAccessKeyId());
        assertEquals("MY_XML_SECRET_KEY",
                     AWSHelper.getInstance().getCredentialsProvider().getCredentials().getAWSSecretKey());
        assertNull("The test configuration must make sure s3Helper is not used,", AWSHelper.getInstance().s3Helper);
    }
}
