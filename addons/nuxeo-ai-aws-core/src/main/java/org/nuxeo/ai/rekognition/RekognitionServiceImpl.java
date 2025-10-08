/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.rekognition;

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import software.amazon.awssdk.services.rekognition.model.SegmentType;
import software.amazon.awssdk.services.rekognition.model.StartSegmentDetectionRequest;
import software.amazon.awssdk.services.rekognition.model.StartSegmentDetectionResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.metrics.AWSMetrics;
import org.nuxeo.ai.sns.NotificationService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.RekognitionClientBuilder;
import software.amazon.awssdk.services.rekognition.model.Attribute;
import software.amazon.awssdk.services.rekognition.model.DetectFacesRequest;
import software.amazon.awssdk.services.rekognition.model.DetectFacesResponse;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.DetectModerationLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.DetectModerationLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.DetectTextRequest;
import software.amazon.awssdk.services.rekognition.model.DetectTextResponse;
import software.amazon.awssdk.services.rekognition.model.FaceAttributes;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.NotificationChannel;
import software.amazon.awssdk.services.rekognition.model.RecognizeCelebritiesRequest;
import software.amazon.awssdk.services.rekognition.model.RecognizeCelebritiesResponse;
import software.amazon.awssdk.services.rekognition.model.StartCelebrityRecognitionRequest;
import software.amazon.awssdk.services.rekognition.model.StartCelebrityRecognitionResponse;
import software.amazon.awssdk.services.rekognition.model.StartContentModerationRequest;
import software.amazon.awssdk.services.rekognition.model.StartContentModerationResponse;
import software.amazon.awssdk.services.rekognition.model.StartFaceDetectionRequest;
import software.amazon.awssdk.services.rekognition.model.StartFaceDetectionResponse;
import software.amazon.awssdk.services.rekognition.model.StartLabelDetectionRequest;
import software.amazon.awssdk.services.rekognition.model.StartLabelDetectionResponse;
import software.amazon.awssdk.services.rekognition.model.Video;

import io.dropwizard.metrics5.Counter;
import io.dropwizard.metrics5.Timer;

/**
 * Implementation of RekognitionService
 */
public class RekognitionServiceImpl extends DefaultComponent implements RekognitionService {

    private static final Logger log = LogManager.getLogger(RekognitionServiceImpl.class);

    protected volatile RekognitionClient client;

    protected AWSMetrics awsMetrics;

    @Override
    public DetectLabelsResponse detectLabels(ManagedBlob blob, int maxResults, float minConfidence) {
        return detectWithClient(blob, (rekognitionClient, image) -> {
            DetectLabelsRequest detectLabelsRequest = DetectLabelsRequest.builder()
                                                                        .maxLabels(maxResults)
                                                                        .minConfidence(minConfidence)
                                                                        .image(image)
                                                                        .build();
            return this.executeRekognitionImageCallWithMetrics(
                    () -> rekognitionClient.detectLabels(detectLabelsRequest),
                    awsMetrics.rekognitionImgLabelDetectionCounter());
        });
    }

    @Override
    public String startDetectLabels(ManagedBlob blob, float minConfidence) {
        NotificationChannel nc = getChannel();
        return startDetectWith(blob, (cl, video) -> {
            StartLabelDetectionRequest request = StartLabelDetectionRequest.builder()
                                                                          .minConfidence(minConfidence)
                                                                          .notificationChannel(nc)
                                                                          .video(video)
                                                                          .build();
            return this.executeRekognitionVideoCallWithMetrics(() -> {
                StartLabelDetectionResponse result = getClient().startLabelDetection(request);
                log.debug("Start label detection completed");
                return result.jobId();
            }, awsMetrics.rekognitionVideoCall());
        });
    }

    @Override
    public DetectTextResponse detectText(ManagedBlob blob) {
        return detectWithClient(blob, (rekognitionClient, image) -> {
            DetectTextRequest detectTextRequest = DetectTextRequest.builder()
                                                                  .image(image)
                                                                  .build();
            return rekognitionClient.detectText(detectTextRequest);
        });
    }

    @Override
    public DetectFacesResponse detectFaces(ManagedBlob blob) {
        return detectWithClient(blob, (rekognitionClient, image) -> {
            DetectFacesRequest detectFacesRequest = DetectFacesRequest.builder()
                                                                     .attributes(Attribute.ALL)
                                                                     .image(image)
                                                                     .build();
            return this.executeRekognitionImageCallWithMetrics(
                    () -> rekognitionClient.detectFaces(detectFacesRequest),
                    awsMetrics.rekognitionImgFaceDetectionCounter());
        });
    }

    @Override
    public String startDetectFaces(ManagedBlob blob) {
        NotificationChannel nc = getChannel();
        return startDetectWith(blob, (cl, video) -> {
            StartFaceDetectionRequest request = StartFaceDetectionRequest.builder()
                                                                        .faceAttributes(FaceAttributes.ALL)
                                                                        .notificationChannel(nc)
                                                                        .video(video)
                                                                        .build();
            return this.executeRekognitionVideoCallWithMetrics(() -> {
                return getClient().startFaceDetection(request).jobId();
            }, awsMetrics.rekognitionVideoCall());
        });
    }

    @Override
    public RecognizeCelebritiesResponse detectCelebrities(ManagedBlob blob) {
        return detectWithClient(blob, (rekognitionClient, image) -> {
            RecognizeCelebritiesRequest recognizeCelebritiesRequest = RecognizeCelebritiesRequest.builder()
                                                                                               .image(image)
                                                                                               .build();
            return this.executeRekognitionImageCallWithMetrics(
                    () -> rekognitionClient.recognizeCelebrities(recognizeCelebritiesRequest),
                    awsMetrics.rekognitionImgFaceDetectionCounter());
        });
    }

    @Override
    public String startDetectCelebrities(ManagedBlob blob) {
        NotificationChannel nc = getChannel();
        return startDetectWith(blob, (cl, video) -> {
            StartCelebrityRecognitionRequest request = StartCelebrityRecognitionRequest.builder()
                                                                                       .notificationChannel(nc)
                                                                                       .video(video)
                                                                                       .build();
            return this.executeRekognitionVideoCallWithMetrics(() -> {
                return getClient().startCelebrityRecognition(request).jobId();
            }, awsMetrics.rekognitionVideoCall());
        });
    }

    @Override
    public DetectModerationLabelsResponse detectUnsafeImages(ManagedBlob blob) {
        return detectWithClient(blob, (rekognitionClient, image) -> {
            DetectModerationLabelsRequest detectModerationLabelsRequest = DetectModerationLabelsRequest.builder()
                                                                                                       .image(image)
                                                                                                       .build();
            return this.executeRekognitionImageCallWithMetrics(
                    () -> rekognitionClient.detectModerationLabels(detectModerationLabelsRequest),
                    awsMetrics.rekognitionImgLabelDetectionCounter());
        });
    }

    @Override
    public String startDetectUnsafeImages(ManagedBlob blob) {
        NotificationChannel nc = getChannel();
        return startDetectWith(blob, (cl, video) -> {
            StartContentModerationRequest request = StartContentModerationRequest.builder()
                                                                                 .notificationChannel(nc)
                                                                                 .video(video)
                                                                                 .build();
            return this.executeRekognitionVideoCallWithMetrics(() -> {
                return getClient().startContentModeration(request).jobId();
            }, awsMetrics.rekognitionVideoCall());
        });
    }

    @Override
    public String startVideoSegmentDetection(ManagedBlob blob, SegmentType segmentType) {
        NotificationChannel nc = getChannel();
        return startDetectWith(blob, (cl, video) -> {
            StartSegmentDetectionRequest request = StartSegmentDetectionRequest.builder()
                                                                              .segmentTypes(segmentType)
                                                                              .notificationChannel(nc)
                                                                              .video(video)
                                                                              .build();
            return this.executeRekognitionVideoCallWithMetrics(() -> {
                return getClient().startSegmentDetection(request).jobId();
            }, awsMetrics.rekognitionVideoCall());
        });
    }

    /**
     * Sets up the Client and Image, then calls AWS using the supplied {@link BiFunction<>}.
     */
    protected <T extends AwsResponse> T detectWithClient(ManagedBlob blob,
            BiFunction<RekognitionClient, Image, T> func) {
        Image image = AWSHelper.getInstance().getImage(blob);
        if (image != null) {
            T result = func.apply(getClient(), image);
            if (log.isDebugEnabled()) {
                log.debug("Result of call to AWS " + result);
            }
            return result;
        }
        return null;
    }

    /**
     * Sets up the Client and Image, then calls AWS using the supplied {@link BiFunction<>}
     */
    protected String startDetectWith(ManagedBlob blob, BiFunction<RekognitionClient, Video, String> func) {
        Video video = AWSHelper.getInstance().getVideo(blob);
        if (video != null) {
            String result = func.apply(getClient(), video);
            if (log.isDebugEnabled()) {
                log.debug("Result JobId " + result);
            }
            return result;
        }
        return null;
    }

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        awsMetrics = Framework.getService(AWSMetrics.class);
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        client = null;
    }

    @Override
    public RekognitionClient getClient() {
        RekognitionClient localClient = client;
        if (localClient == null) {
            synchronized (this) {
                localClient = client;
                if (localClient == null) {
                    client = localClient = RekognitionClient.builder()
                                                          .credentialsProvider(AWSHelper.getInstance().getCredentialsProvider())
                                                          .region(AWSHelper.getInstance().getRegion())
                                                          .build();
                }
            }
        }
        return localClient;
    }

    private NotificationChannel getChannel() {
        NotificationService service = Framework.getService(NotificationService.class);
        String topicArn = service.getTopicArnFor("detect");
        return NotificationChannel.builder()
                                 .roleArn("arn:aws:iam::role/RekognitionServiceRole") // Default role ARN
                                 .snsTopicArn(topicArn)
                                 .build();
    }

    protected <T> T executeRekognitionVideoCallWithMetrics(Supplier<T> supplier, Timer timer) {
        Timer.Context responseTime = timer.time();
        T result = supplier.get();
        long elapsed = responseTime.stop();
        awsMetrics.incrementRekognitionGlobalCalls();
        awsMetrics.rekognitionVideoCall().update(elapsed, TimeUnit.NANOSECONDS);
        return result;
    }

    protected <T> T executeRekognitionImageCallWithMetrics(Supplier<T> supplier, Counter counter) {
        T result = supplier.get();
        counter.inc();
        awsMetrics.incrementRekognitionGlobalCalls();
        awsMetrics.incrementRekognitionImgCalls();
        return result;
    }
}
