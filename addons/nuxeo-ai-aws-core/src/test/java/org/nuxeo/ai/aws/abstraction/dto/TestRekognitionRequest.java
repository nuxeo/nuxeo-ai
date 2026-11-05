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
package org.nuxeo.ai.aws.abstraction.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * The requests can be created from the data of the image or from a S3 reference, with or without the version.
 */
public class TestRekognitionRequest {

    @Test
    public void labelsRequest() {
        RekognitionRequest.DetectLabels bytes = new RekognitionRequest.DetectLabels(new byte[] { 1 }, 10, 0.5f);
        assertThat(bytes.isS3Reference()).isFalse();
        assertThat(bytes.s3Version()).isNull();

        RekognitionRequest.DetectLabels s3 = new RekognitionRequest.DetectLabels("bucket", "key", 10, 0.5f);
        assertThat(s3.isS3Reference()).isTrue();
        assertThat(s3.s3Version()).isNull();

        RekognitionRequest.DetectLabels versioned = new RekognitionRequest.DetectLabels("bucket", "key", "v7", 10,
                0.5f);
        assertThat(versioned.isS3Reference()).isTrue();
        assertThat(versioned.s3Version()).isEqualTo("v7");
        assertThat(versioned.maxLabels()).isEqualTo(10);
    }

    @Test
    public void facesRequest() {
        RekognitionRequest.DetectFaces bytes = new RekognitionRequest.DetectFaces(new byte[] { 1 }, true);
        assertThat(bytes.isS3Reference()).isFalse();
        assertThat(bytes.s3Version()).isNull();

        RekognitionRequest.DetectFaces s3 = new RekognitionRequest.DetectFaces("bucket", "key", true);
        assertThat(s3.isS3Reference()).isTrue();
        assertThat(s3.s3Version()).isNull();

        RekognitionRequest.DetectFaces versioned = new RekognitionRequest.DetectFaces("bucket", "key", "v7", true);
        assertThat(versioned.s3Version()).isEqualTo("v7");
        assertThat(versioned.includeAttributes()).isTrue();
    }

    @Test
    public void textRequest() {
        RekognitionRequest.DetectText bytes = new RekognitionRequest.DetectText(new byte[] { 1 });
        assertThat(bytes.isS3Reference()).isFalse();
        assertThat(bytes.s3Version()).isNull();

        RekognitionRequest.DetectText s3 = new RekognitionRequest.DetectText("bucket", "key");
        assertThat(s3.isS3Reference()).isTrue();
        assertThat(s3.s3Version()).isNull();

        RekognitionRequest.DetectText versioned = new RekognitionRequest.DetectText("bucket", "key", "v7");
        assertThat(versioned.s3Version()).isEqualTo("v7");
    }
}
