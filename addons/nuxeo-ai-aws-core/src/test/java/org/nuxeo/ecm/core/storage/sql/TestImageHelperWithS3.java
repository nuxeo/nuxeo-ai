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
package org.nuxeo.ecm.core.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.blob.s3.S3BlobStoreConfiguration;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import software.amazon.awssdk.services.s3.S3Client;

/**
 * The S3 object of a blob must be the one the S3BlobStore reads: without the provider prefix, with the bucket prefix
 * and sub directories, and with its version on a versioned bucket.
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
public class TestImageHelperWithS3 {

    protected static final String BUCKET = "my-bucket";

    protected static final String PREFIX = "nuxeo-ai-5x/";

    protected static final String DIGEST = "abcdef0123456789";

    /**
     * S3BlobStoreConfiguration which doesn't call AWS to create its client or to find out if the bucket is versioned,
     * and which tells if the keys have a version.
     */
    protected static class OfflineS3Configuration extends S3BlobStoreConfiguration {

        protected static volatile boolean versioned;

        public OfflineS3Configuration(Map<String, String> properties) throws IOException {
            super(properties);
        }

        @Override
        protected S3Client createSyncClient() {
            return null;
        }

        @Override
        protected boolean computeVersioningEnabled() {
            return versioned;
        }

        @Override
        public boolean useVersion() {
            // keys with a version are used with the document id key strategy on a versioned bucket
            return versioned;
        }
    }

    protected static S3BlobStoreConfiguration config(int subDirsDepth, boolean versioned) throws IOException {
        OfflineS3Configuration.versioned = versioned;
        Map<String, String> properties = new HashMap<>();
        properties.put("bucket", BUCKET);
        properties.put("bucket_prefix", PREFIX);
        properties.put("region", "us-east-1");
        properties.put("awsid", "id");
        properties.put("awssecret", "secret");
        properties.put("subDirsDepth", String.valueOf(subDirsDepth));
        return new OfflineS3Configuration(properties);
    }

    /** An ImageHelperWithS3 for blobs stored with a S3BlobProvider using the given configuration */
    protected static ImageHelperWithS3 helper(S3BlobStoreConfiguration config) {
        return new ImageHelperWithS3() {
            @Override
            protected S3BlobStoreConfiguration getConfiguration(ManagedBlob blob) {
                return config;
            }
        };
    }

    protected static ManagedBlob blob(String key) {
        return new BlobMetaImpl("mys3", "image/png", key, DIGEST, null, 1L);
    }

    @Test
    public void defaultLayout() throws IOException {
        software.amazon.awssdk.services.rekognition.model.S3Object s3Object = helper(config(0, false))
                                                                                                      .getImage(blob(
                                                                                                              DIGEST))
                                                                                                      .s3Object();
        assertThat(s3Object.bucket()).isEqualTo(BUCKET);
        assertThat(s3Object.name()).isEqualTo(PREFIX + DIGEST);
        assertThat(s3Object.version()).isNull();
    }

    @Test
    public void providerPrefixIsRemovedFromTheKey() throws IOException {
        software.amazon.awssdk.services.rekognition.model.S3Object s3Object = helper(config(0, false)).getVideo(
                blob("mys3:" + DIGEST)).s3Object();
        assertThat(s3Object.bucket()).isEqualTo(BUCKET);
        assertThat(s3Object.name()).isEqualTo(PREFIX + DIGEST);
    }

    @Test
    public void objectIsInSubDirectories() throws IOException {
        S3BlobStoreConfiguration config = config(2, false);
        String name = helper(config).getImage(blob(DIGEST)).s3Object().name();
        assertThat(name).isEqualTo(PREFIX + "ab/cd/" + DIGEST);
        // with the provider prefix too
        assertThat(helper(config).getImage(blob("mys3:" + DIGEST)).s3Object().name()).isEqualTo(name);
    }

    @Test
    public void versionOfVersionedBucketIsKept() throws IOException {
        S3BlobStoreConfiguration config = config(0, true);
        String key = "mys3:" + DIGEST + "@v7";

        software.amazon.awssdk.services.rekognition.model.S3Object video = helper(config).getVideo(blob(key))
                                                                                         .s3Object();
        assertThat(video.name()).isEqualTo(PREFIX + DIGEST);
        assertThat(video.version()).isEqualTo("v7");

        software.amazon.awssdk.services.textract.model.S3Object document = helper(config).getDocument(blob(key))
                                                                                         .s3Object();
        assertThat(document.bucket()).isEqualTo(BUCKET);
        assertThat(document.name()).isEqualTo(PREFIX + DIGEST);
        assertThat(document.version()).isEqualTo("v7");
    }

    @Test
    public void noS3ReferenceWithoutS3BlobProvider() {
        ImageHelperWithS3 helper = new ImageHelperWithS3();
        assertThat(helper.getImage(null)).isNull();
        assertThat(helper.getImage(blob(null))).isNull();
        // blob stored with another provider
        assertThat(helper.getVideo(blob(DIGEST))).isNull();
        assertThat(helper.getDocument(blob(DIGEST))).isNull();
    }
}
