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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.storage.sql.ImageHelperWithS3;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy("org.nuxeo.ai.aws.aws-core")
@Deploy("org.nuxeo.runtime.aws")
@Deploy("org.nuxeo.ai.aws.aws-core:OSGI-INF/test-aws-config.xml")
public class TestAWSHelper {

    @Test
    public void testCustomCredentials() {
        assertEquals("MY_REGION", AWSHelper.getInstance().getRegion().id());
        assertEquals("MY_XML_ACCESS_KEY_ID",
                AWSHelper.getInstance().getCredentialsProvider().resolveCredentials().accessKeyId());
        assertEquals("MY_XML_SECRET_KEY",
                AWSHelper.getInstance().getCredentialsProvider().resolveCredentials().secretAccessKey());
    }

    @Test
    public void testS3DetectionClassExists() throws ClassNotFoundException {
        // S3BinaryManager is gone in LTS 2025, S3 support must be detected from S3BlobProvider (NXCON-375)
        assertNotNull(Class.forName(AWSHelper.S3_BLOB_PROVIDER_NAME));
    }

    @Test(expected = ClassNotFoundException.class)
    public void testS3BinaryManagerRemoved() throws ClassNotFoundException {
        Class.forName("org.nuxeo.ecm.core.storage.sql.S3BinaryManager");
    }

    @Test
    public void testNoVideoReferenceWithoutS3BlobProvider() {
        ImageHelperWithS3 s3Helper = new ImageHelperWithS3();
        assertNull(s3Helper.getVideo(null));
        ManagedBlob blob = new BlobMetaImpl("test", "video/mp4", "aKey", "aDigest", null, 1L);
        assertNull(s3Helper.getVideo(blob));
    }
}
