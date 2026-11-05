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
package org.nuxeo.ai.aws.abstraction.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;
import org.nuxeo.ecm.core.api.NuxeoException;

import software.amazon.awssdk.services.rekognition.model.Image;

/**
 * The image sent to Rekognition must reference the same S3 object version as the blob.
 */
public class TestRekognitionServiceFacadeImpl {

    @Test
    public void s3ReferenceKeepsTheVersion() {
        Image image = RekognitionServiceFacadeImpl.buildImage(null, "bucket", "prefix/key", "v7");
        assertThat(image.bytes()).isNull();
        assertThat(image.s3Object().bucket()).isEqualTo("bucket");
        assertThat(image.s3Object().name()).isEqualTo("prefix/key");
        assertThat(image.s3Object().version()).isEqualTo("v7");
    }

    @Test
    public void s3ReferenceWithoutVersion() {
        Image image = RekognitionServiceFacadeImpl.buildImage(null, "bucket", "prefix/key", null);
        assertThat(image.s3Object().name()).isEqualTo("prefix/key");
        assertThat(image.s3Object().version()).isNull();
    }

    @Test
    public void bytesImage() {
        Image image = RekognitionServiceFacadeImpl.buildImage(new byte[] { 1, 2, 3 }, null, null, null);
        assertThat(image.bytes().asByteArray()).containsExactly(1, 2, 3);
        assertThat(image.s3Object()).isNull();
    }

    @Test
    public void imageNeedsDataOrS3Reference() {
        assertThatThrownBy(() -> RekognitionServiceFacadeImpl.buildImage(null, null, null, null)).isInstanceOf(
                NuxeoException.class);
    }
}
