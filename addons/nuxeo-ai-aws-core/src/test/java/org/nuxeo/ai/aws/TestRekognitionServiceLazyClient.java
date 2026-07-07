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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * NXCON-300: Verifies that RekognitionService lazily initializes the AWS client.
 * With test-aws-config-valid-region.xml deployed, the factory is available and getClient() returns a real client.
 * Legacy methods fail gracefully with null blobs (before reaching actual AWS calls).
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy("org.nuxeo.runtime.aws")
@Deploy("org.nuxeo.ai.aws.aws-core")
@Deploy("org.nuxeo.ai.aws.aws-core:OSGI-INF/test-aws-config-valid-region.xml")
public class TestRekognitionServiceLazyClient {

    @Inject
    protected RekognitionService rekognitionService;

    @Test
    public void getClientReturnsNonNullWhenConfigured() {
        assertThat(rekognitionService.getClient()).isNotNull();
    }

    @Test
    public void getClientReturnsSameInstance() {
        assertThat(rekognitionService.getClient()).isSameAs(rekognitionService.getClient());
    }

    @Test
    public void startLabelDetectionFailsGracefullyWithNullBlob() {
        assertThatThrownBy(() -> rekognitionService.startLabelDetection(null, 0.5f))
                .isInstanceOf(NuxeoException.class);
    }

    @Test
    public void startDetectFacesFailsGracefullyWithNullBlob() {
        assertThatThrownBy(() -> rekognitionService.startDetectFaces(null))
                .isInstanceOf(NuxeoException.class);
    }

    @Test
    public void startDetectCelebritiesFailsGracefullyWithNullBlob() {
        assertThatThrownBy(() -> rekognitionService.startDetectCelebrities(null))
                .isInstanceOf(NuxeoException.class);
    }

    @Test
    public void startDetectUnsafeImagesFailsGracefullyWithNullBlob() {
        assertThatThrownBy(() -> rekognitionService.startDetectUnsafeImages(null))
                .isInstanceOf(NuxeoException.class);
    }

    @Test
    public void detectFacesLegacyFailsGracefullyWithNullBlob() {
        assertThatThrownBy(() -> rekognitionService.detectFaces(null))
                .isInstanceOf(Exception.class);
    }

    @Test
    public void detectCelebritiesFailsGracefullyWithNullBlob() {
        assertThatThrownBy(() -> rekognitionService.detectCelebrities(null))
                .isInstanceOf(Exception.class);
    }

    @Test
    public void startVideoSegmentDetectionFailsGracefullyWithNullBlob() {
        assertThatThrownBy(() -> rekognitionService.startVideoSegmentDetection(null, null))
                .isInstanceOf(Exception.class);
    }
}
