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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;

import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.NotificationChannel;
import software.amazon.awssdk.services.rekognition.model.S3Object;
import software.amazon.awssdk.services.rekognition.model.SegmentType;
import software.amazon.awssdk.services.rekognition.model.Video;

/**
 * Rekognition only publishes the video job completion to SNS when the start request has a NotificationChannel,
 * otherwise the results never reach Nuxeo.
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy("org.nuxeo.runtime.aws")
@Deploy("org.nuxeo.ai.aws.aws-core")
@Deploy("org.nuxeo.ai.aws.aws-core:OSGI-INF/test-aws-config-valid-region.xml")
@Deploy("org.nuxeo.ai.aws.aws-core:OSGI-INF/test-aws-sns.xml")
public class TestRekognitionNotificationChannel {

    protected static final String TOPIC_ARN = "arn:aws:sns:us-east-1:783725821734:ai-rekognition-test-topic";

    protected static final String ROLE_ARN = "arn:aws:iam::123456789012:role/rekognition-sns";

    @Inject
    protected RekognitionService rekognitionService;

    protected final List<Object> requests = new ArrayList<>();

    protected RekognitionClient previousClient;

    protected ManagedBlob video = new BlobMetaImpl("test", "video/mp4", "videoKey", "videoDigest", null, 1L);

    @Before
    public void stubAWS() {
        VideoAWSHelper.install();
        RekognitionServiceImpl impl = (RekognitionServiceImpl) rekognitionService;
        previousClient = impl.rekognitionClient;
        // Captures the start request and answers with a job id instead of calling AWS
        impl.rekognitionClient = (RekognitionClient) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[] { RekognitionClient.class }, (proxy, method, args) -> {
                    requests.add(args[0]);
                    Method builderMethod = method.getReturnType().getMethod("builder");
                    Class<?> builderType = builderMethod.getReturnType();
                    Object builder = builderMethod.invoke(null);
                    builderType.getMethod("jobId", String.class).invoke(builder, "jobId");
                    return builderType.getMethod("build").invoke(builder);
                });
    }

    @After
    public void restoreAWS() {
        VideoAWSHelper.uninstall();
        ((RekognitionServiceImpl) rekognitionService).rekognitionClient = previousClient;
    }

    @Test
    @WithFrameworkProperty(name = "nuxeo.ai.aws.rekognition.role.arn", value = ROLE_ARN)
    public void videoJobsAreStartedWithNotificationChannel() throws ReflectiveOperationException {
        assertThat(rekognitionService.startLabelDetection(video, 0.5f)).isEqualTo("jobId");
        assertThat(rekognitionService.startDetectFaces(video)).isEqualTo("jobId");
        assertThat(rekognitionService.startDetectCelebrities(video)).isEqualTo("jobId");
        assertThat(rekognitionService.startDetectUnsafeImages(video)).isEqualTo("jobId");
        assertThat(rekognitionService.startVideoSegmentDetection(video, SegmentType.SHOT)).isEqualTo("jobId");

        assertThat(requests).hasSize(5);
        for (Object request : requests) {
            NotificationChannel channel = (NotificationChannel) request.getClass()
                                                                       .getMethod("notificationChannel")
                                                                       .invoke(request);
            assertThat(channel).as(request.getClass().getSimpleName()).isNotNull();
            assertThat(channel.snsTopicArn()).isEqualTo(TOPIC_ARN);
            assertThat(channel.roleArn()).isEqualTo(ROLE_ARN);
        }
    }

    @Test
    @WithFrameworkProperty(name = "nuxeo.ai.aws.rekognition.role.arn", value = " ")
    public void videoJobFailsWithBlankRoleArn() {
        assertThatThrownBy(() -> rekognitionService.startLabelDetection(video, 0.5f)).isInstanceOf(
                NuxeoException.class).hasMessageContaining("nuxeo.ai.aws.rekognition.role.arn");
        assertThat(requests).isEmpty();
    }

    @Test
    public void videoJobFailsWithoutRoleArn() {
        assertThatThrownBy(() -> rekognitionService.startLabelDetection(video, 0.5f)).isInstanceOf(
                NuxeoException.class).hasMessageContaining("nuxeo.ai.aws.rekognition.role.arn");
        assertThat(requests).isEmpty();
    }

    /**
     * AWSHelper returning a S3 video reference without needing a S3 blob provider.
     */
    protected static class VideoAWSHelper extends AWSHelper {

        protected static AWSHelper previous;

        protected static void install() {
            previous = INSTANCE;
            INSTANCE = new VideoAWSHelper();
        }

        protected static void uninstall() {
            INSTANCE = previous;
        }

        @Override
        public Video getVideo(ManagedBlob managedBlob) {
            return Video.builder()
                        .s3Object(S3Object.builder().bucket("bucket").name(managedBlob.getKey()).build())
                        .build();
        }
    }
}
