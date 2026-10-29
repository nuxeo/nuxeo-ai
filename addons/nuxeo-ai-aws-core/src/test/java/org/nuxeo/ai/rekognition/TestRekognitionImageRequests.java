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
package org.nuxeo.ai.rekognition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.aws.abstraction.RekognitionServiceFacade;
import org.nuxeo.ai.aws.abstraction.dto.RekognitionRequest;
import org.nuxeo.ai.aws.dto.RekognitionResult;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.S3Object;

/**
 * Images stored in S3 are sent to Rekognition as S3 references, other images as bytes.
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy("org.nuxeo.runtime.aws")
@Deploy("org.nuxeo.ai.aws.aws-core")
@Deploy("org.nuxeo.ai.aws.aws-core:OSGI-INF/test-aws-config-valid-region.xml")
public class TestRekognitionImageRequests {

    @Inject
    protected RekognitionService rekognitionService;

    protected final List<Object> requests = new ArrayList<>();

    protected RekognitionServiceFacade previousFacade;

    protected ManagedBlob blob = new BlobMetaImpl("test", "image/png", "imageKey", "imageDigest", null, 1L);

    @Before
    public void stubAWS() {
        RekognitionServiceImpl impl = (RekognitionServiceImpl) rekognitionService;
        previousFacade = impl.rekognitionFacade;
        impl.rekognitionFacade = new RecordingFacade();
    }

    @After
    public void restoreAWS() {
        ImageAWSHelper.uninstall();
        ((RekognitionServiceImpl) rekognitionService).rekognitionFacade = previousFacade;
    }

    @Test
    public void imageInS3IsSentAsS3Reference() {
        ImageAWSHelper.install(
                Image.builder().s3Object(S3Object.builder().bucket("bucket").name("prefix/imageKey").build()).build());
        callAllImageOperations();

        assertThat(requests).hasSize(5);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request).hasFieldOrPropertyWithValue("s3Bucket", "bucket");
            assertThat(request).hasFieldOrPropertyWithValue("s3Key", "prefix/imageKey");
            assertThat(request).hasFieldOrPropertyWithValue("imageData", null);
        });
    }

    @Test
    public void imageNotInS3IsSentAsBytes() {
        byte[] bytes = { 1, 2, 3 };
        ImageAWSHelper.install(Image.builder().bytes(SdkBytes.fromByteArray(bytes)).build());
        callAllImageOperations();

        assertThat(requests).hasSize(5);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request).hasFieldOrPropertyWithValue("s3Bucket", null);
            assertThat(request).hasFieldOrPropertyWithValue("imageData", bytes);
        });
    }

    protected void callAllImageOperations() {
        rekognitionService.detectLabels(blob, 10, 0.5f);
        rekognitionService.detectModerationLabels(blob, 0.5f);
        rekognitionService.detectText(blob);
        rekognitionService.detectFaces(blob, Collections.singleton("ALL"));
        rekognitionService.recognizeCelebrities(blob);
    }

    /**
     * Facade recording the requests instead of calling AWS.
     */
    protected class RecordingFacade implements RekognitionServiceFacade {

        @Override
        public List<RekognitionResult.Label> detectLabels(RekognitionRequest.DetectLabels request) {
            requests.add(request);
            return Collections.emptyList();
        }

        @Override
        public List<RekognitionResult.Face> detectFaces(RekognitionRequest.DetectFaces request) {
            requests.add(request);
            return Collections.emptyList();
        }

        @Override
        public List<RekognitionResult.TextDetection> detectText(RekognitionRequest.DetectText request) {
            requests.add(request);
            return Collections.emptyList();
        }

        @Override
        public List<RekognitionResult.ModerationLabel> detectModerationLabels(RekognitionRequest.DetectLabels request) {
            requests.add(request);
            return Collections.emptyList();
        }

        @Override
        public List<RekognitionResult.Celebrity> recognizeCelebrities(RekognitionRequest.DetectFaces request) {
            requests.add(request);
            return Collections.emptyList();
        }
    }

    /**
     * AWSHelper returning a given Rekognition image.
     */
    protected static class ImageAWSHelper extends AWSHelper {

        protected static AWSHelper previous;

        protected final Image image;

        protected ImageAWSHelper(Image image) {
            this.image = image;
        }

        protected static void install(Image image) {
            previous = INSTANCE;
            INSTANCE = new ImageAWSHelper(image);
        }

        protected static void uninstall() {
            if (INSTANCE instanceof ImageAWSHelper) {
                INSTANCE = previous;
            }
        }

        @Override
        public Image getImage(ManagedBlob managedBlob) {
            return image;
        }
    }
}
