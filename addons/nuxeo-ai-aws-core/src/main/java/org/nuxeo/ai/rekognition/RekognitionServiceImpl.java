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
import com.amazonaws.AmazonWebServiceResult;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.Attribute;
import com.amazonaws.services.rekognition.model.DetectFacesRequest;
import com.amazonaws.services.rekognition.model.DetectFacesResult;
import com.amazonaws.services.rekognition.model.DetectLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.DetectModerationLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectModerationLabelsResult;
import com.amazonaws.services.rekognition.model.DetectTextRequest;
import com.amazonaws.services.rekognition.model.DetectTextResult;
import com.amazonaws.services.rekognition.model.FaceAttributes;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.NotificationChannel;
import com.amazonaws.services.rekognition.model.RecognizeCelebritiesRequest;
import com.amazonaws.services.rekognition.model.RecognizeCelebritiesResult;
import com.amazonaws.services.rekognition.model.StartCelebrityRecognitionRequest;
import com.amazonaws.services.rekognition.model.StartCelebrityRecognitionResult;
import com.amazonaws.services.rekognition.model.StartContentModerationRequest;
import com.amazonaws.services.rekognition.model.StartContentModerationResult;
import com.amazonaws.services.rekognition.model.StartFaceDetectionRequest;
import com.amazonaws.services.rekognition.model.StartFaceDetectionResult;
import com.amazonaws.services.rekognition.model.StartLabelDetectionRequest;
import com.amazonaws.services.rekognition.model.StartLabelDetectionResult;
import com.amazonaws.services.rekognition.model.Video;

import io.dropwizard.metrics5.Counter;
import io.dropwizard.metrics5.Timer;

/**
 * Implementation of RekognitionService
 */
public class RekognitionServiceImpl extends DefaultComponent implements RekognitionService {

    private static final Logger log = LogManager.getLogger(RekognitionServiceImpl.class);

    protected volatile AmazonRekognition client;

    protected AWSMetrics awsMetrics;

    @Override
    public DetectLabelsResult detectLabels(ManagedBlob blob, int maxResults, float minConfidence) {
        return detectWithClient(blob, (rekognitionClient, image) -> {
            DetectLabelsRequest detectLabelsRequest = new DetectLabelsRequest().withMaxLabels(maxResults)
                                                                               .withMinConfidence(minConfidence)
                                                                               .withImage(image);
            return this.executeRekognitionImageCallWithMetrics(
                    () -> rekognitionClient.detectLabels(detectLabelsRequest),
                    awsMetrics.rekognitionImgLabelDetectionCounter());
        });
    }

    @Override
    public String startDetectLabels(ManagedBlob blob, float minConfidence) {
        NotificationChannel nc = getChannel();
        return startDetectWith(blob, (cl, video) -> {
            StartLabelDetectionRequest request = new StartLabelDetectionRequest().withMinConfidence(minConfidence)
                                                                                 .withNotificationChannel(nc)
                                                                                 .withVideo(video);
            return this.executeRekognitionVideoCallWithMetrics(() -> {
                StartLabelDetectionResult result = getClient().startLabelDetection(request);
                log.debug("Start label detection status code {}", result.getSdkHttpMetadata().getHttpStatusCode());
                return result.getJobId();
            }, awsMetrics.rekognitionVideoLabelDetectionCall());
        });
    }

    @Override
    public DetectTextResult detectText(ManagedBlob blob) {
        return detectWithClient(blob, (rekognitionClient, image) -> {
            DetectTextRequest request = new DetectTextRequest().withImage(image);
            return this.executeRekognitionImageCallWithMetrics(() -> rekognitionClient.detectText(request),
                    awsMetrics.rekognitionImgTextDetectionCounter());
        });
    }

    @Override
    public DetectFacesResult detectFaces(ManagedBlob blob, Attribute... attributes) {
        return detectWithClient(blob, (rekognitionClient, image) -> {
            DetectFacesRequest request = new DetectFacesRequest().withImage(image).withAttributes(attributes);
            return this.executeRekognitionImageCallWithMetrics(() -> rekognitionClient.detectFaces(request),
                    awsMetrics.rekognitionImgFaceDetectionCounter());
        });
    }

    @Override
    public String startDetectFaces(ManagedBlob blob, FaceAttributes attributes) {
        NotificationChannel nc = getChannel();
        return startDetectWith(blob, (cl, video) -> {
            StartFaceDetectionRequest request = new StartFaceDetectionRequest().withFaceAttributes(attributes)
                                                                               .withNotificationChannel(nc)
                                                                               .withVideo(video);
            return this.executeRekognitionVideoCallWithMetrics(() -> {
                StartFaceDetectionResult result = getClient().startFaceDetection(request);
                return result.getJobId();
            }, awsMetrics.rekognitionVideoFaceDetectionCall());
        });
    }

    @Override
    public RecognizeCelebritiesResult detectCelebrityFaces(ManagedBlob blob) {
        return detectWithClient(blob, (rekognitionClient, image) -> {
            RecognizeCelebritiesRequest request = new RecognizeCelebritiesRequest().withImage(image);
            return this.executeRekognitionImageCallWithMetrics(() -> rekognitionClient.recognizeCelebrities(request),
                    awsMetrics.rekognitionImgCelebritiesDetectionCounter());
        });
    }

    @Override
    public String startDetectCelebrityFaces(ManagedBlob blob) {
        NotificationChannel nc = getChannel();
        return startDetectWith(blob, (cl, video) -> {
            StartCelebrityRecognitionRequest request = new StartCelebrityRecognitionRequest().withNotificationChannel(
                    nc).withVideo(video);
            return this.executeRekognitionVideoCallWithMetrics(() -> {
                StartCelebrityRecognitionResult result = getClient().startCelebrityRecognition(request);
                return result.getJobId();
            }, awsMetrics.rekognitionVideoCelebritiesDetectionCall());
        });
    }

    @Override
    public DetectModerationLabelsResult detectUnsafeImages(ManagedBlob blob) {
        return detectWithClient(blob, (rekognitionClient, image) -> {
            DetectModerationLabelsRequest request = new DetectModerationLabelsRequest().withImage(image);
            return this.executeRekognitionImageCallWithMetrics(() -> rekognitionClient.detectModerationLabels(request),
                    awsMetrics.rekognitionImgUnsafeDetectionCounter());
        });
    }

    @Override
    public String startDetectUnsafe(ManagedBlob blob) {
        NotificationChannel nc = getChannel();
        return startDetectWith(blob, (cl, video) -> {
            StartContentModerationRequest request = new StartContentModerationRequest().withNotificationChannel(nc)
                                                                                       .withVideo(video);
            return this.executeRekognitionVideoCallWithMetrics(() -> {
                StartContentModerationResult result = getClient().startContentModeration(request);
                return result.getJobId();
            }, awsMetrics.rekognitionVideoUnsafeDetectionCall());
        });
    }

    /**
     * Sets up the Client and Image, then calls AWS using the supplied {@link BiFunction<>}.
     */
    protected <T extends AmazonWebServiceResult> T detectWithClient(ManagedBlob blob,
            BiFunction<AmazonRekognition, Image, T> func) {
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
    protected String startDetectWith(ManagedBlob blob, BiFunction<AmazonRekognition, Video, String> func) {
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
    public AmazonRekognition getClient() {
        AmazonRekognition localClient = client;
        if (localClient == null) {
            synchronized (this) {
                localClient = client;
                if (localClient == null) {
                    AmazonRekognitionClientBuilder builder = AmazonRekognitionClientBuilder.standard()
                                                                                           .withCredentials(
                                                                                                   AWSHelper.getInstance()
                                                                                                            .getCredentialsProvider())
                                                                                           .withRegion(
                                                                                                   AWSHelper.getInstance()
                                                                                                            .getRegion());
                    client = localClient = builder.build();
                }
            }
        }
        return localClient;
    }

    protected NotificationChannel getChannel() {
        NotificationService ns = Framework.getService(NotificationService.class);
        String arn = (String) Framework.getProperties().get("nuxeo.ai.aws.rekognition.role.arn");
        if (StringUtils.isEmpty(arn)) {
            throw new NuxeoException("Missing Role ARN; Add `nuxeo.ai.aws.rekognition.role.arn` to nuxeo.conf");
        }

        return new NotificationChannel().withSNSTopicArn(ns.getTopicArnFor(DETECT_SNS_TOPIC)).withRoleArn(arn);
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
