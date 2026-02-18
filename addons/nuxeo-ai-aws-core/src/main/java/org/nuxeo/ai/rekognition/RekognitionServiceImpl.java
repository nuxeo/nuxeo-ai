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

import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.aws.abstraction.AWSServiceRegistry;
import org.nuxeo.ai.aws.abstraction.RekognitionServiceFacade;
import org.nuxeo.ai.aws.abstraction.dto.RekognitionRequest;
import org.nuxeo.ai.aws.dto.LabelsResult;
import org.nuxeo.ai.aws.dto.RekognitionResult;
import org.nuxeo.ai.aws.dto.TextDetectionResult;
import org.nuxeo.ai.metrics.AWSMetrics;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

/**
 * Rekognition Service Implementation - NOW USES ABSTRACTION LAYER! NO AWS SDK IMPORTS! All AWS SDK dependencies are
 * completely isolated in the facade layer. This service now only depends on our abstraction DTOs and interfaces.
 *
 * @since 2.1.2
 */
public class RekognitionServiceImpl extends DefaultComponent implements RekognitionService {

    private static final Log log = LogFactory.getLog(RekognitionServiceImpl.class);

    protected RekognitionServiceFacade rekognitionFacade;

    protected AWSMetrics awsMetrics;

    protected RekognitionClient rekognitionClient; // underlying AWS client for legacy operations

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        // Get facade through registry - no AWS SDK dependencies
        AWSServiceRegistry registry = Framework.getService(AWSServiceRegistry.class);
        rekognitionFacade = registry.getRekognitionService();
        awsMetrics = Framework.getService(AWSMetrics.class);
        // Legacy client access
        rekognitionClient = Framework.getService(org.nuxeo.ai.aws.AWSClientFactory.class).getRekognitionClient();
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        rekognitionFacade = null;
        awsMetrics = null;
        rekognitionClient = null;
    }

    // Existing abstraction-based detectLabels kept
    @Override
    public LabelsResult detectLabels(ManagedBlob blob, int maxResults, float minConfidence) {
        if (log.isDebugEnabled()) {
            log.debug("Calling detectLabels for " + blob.getKey());
        }

        try {
            // Create abstraction DTO instead of AWS SDK request
            RekognitionRequest.DetectLabels request = createDetectLabelsRequest(blob, maxResults, minConfidence);

            // Call through facade - no AWS SDK objects involved
            List<RekognitionResult.Label> labels = rekognitionFacade.detectLabels(request);

            if (log.isDebugEnabled()) {
                log.debug("DetectLabelsResult: " + labels.size() + " labels detected");
            }

            if (awsMetrics != null) {
                awsMetrics.updateRekognitionImageUnits(1L);
            }

            // Convert to existing DTO format for backward compatibility
            return convertToLabelsResult(labels);

        } catch (SdkServiceException e) {
            // Let AWS SDK exceptions propagate for AWSHelper.handlingExceptions() to classify
            // as fatal (e.g. AccessDeniedException) or retriable
            throw e;
        } catch (Exception e) {
            log.error("Error detecting labels for blob: " + blob.getKey(), e);
            throw new NuxeoException("Failed to detect labels", e);
        }
    }

    // Legacy async video label detection
    @Override
    public String startLabelDetection(ManagedBlob blob, float minConfidence) {
        Video video = AWSHelper.getInstance().getVideo(blob);
        if (video == null) {
            throw new NuxeoException("Blob is not a video (no video reference available)");
        }
        StartLabelDetectionRequest request = StartLabelDetectionRequest.builder()
                                                                       .video(video)
                                                                       .minConfidence(minConfidence)
                                                                       .build();
        StartLabelDetectionResponse response = rekognitionClient.startLabelDetection(request);
        return response.jobId();
    }

    @Override
    public LabelsResult detectModerationLabels(ManagedBlob blob, float minConfidence) {
        if (log.isDebugEnabled()) {
            log.debug("Calling detectModerationLabels for " + blob.getKey());
        }

        try {
            // Create abstraction DTO instead of AWS SDK request
            RekognitionRequest.DetectLabels request = createDetectLabelsRequest(blob, 1000, minConfidence);

            // Call through facade - no AWS SDK objects involved
            List<RekognitionResult.ModerationLabel> moderationLabels = rekognitionFacade.detectModerationLabels(
                    request);

            if (log.isDebugEnabled()) {
                log.debug("DetectModerationLabelsResult: " + moderationLabels.size() + " moderation labels detected");
            }

            if (awsMetrics != null) {
                awsMetrics.updateRekognitionImageUnits(1L);
            }

            // Convert to existing DTO format for backward compatibility
            return convertModerationLabelsToLabelsResult(moderationLabels);

        } catch (SdkServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error detecting moderation labels for blob: " + blob.getKey(), e);
            throw new NuxeoException("Failed to detect moderation labels", e);
        }
    }

    @Override
    public TextDetectionResult detectText(ManagedBlob blob) {
        if (log.isDebugEnabled()) {
            log.debug("Calling detectText for " + blob.getKey());
        }

        try {
            // Create abstraction DTO instead of AWS SDK request
            RekognitionRequest.DetectText request = createDetectTextRequest(blob);

            // Call through facade - no AWS SDK objects involved
            List<RekognitionResult.TextDetection> textDetections = rekognitionFacade.detectText(request);

            if (log.isDebugEnabled()) {
                log.debug("DetectTextResult: " + textDetections.size() + " text detections found");
            }

            if (awsMetrics != null) {
                awsMetrics.updateRekognitionImageUnits(1L);
            }

            // Convert to existing DTO format for backward compatibility
            return convertToTextDetectionResult(textDetections);

        } catch (SdkServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error detecting text for blob: " + blob.getKey(), e);
            throw new NuxeoException("Failed to detect text", e);
        }
    }

    // Domain faces with attribute collection (image use-case returning LabelsResult)
    @Override
    public LabelsResult detectFaces(ManagedBlob blob, Collection<String> attributes) {
        try {
            boolean includeAttributes = attributes != null && !attributes.isEmpty();
            RekognitionRequest.DetectFaces request = createDetectFacesRequest(blob, includeAttributes);
            List<RekognitionResult.Face> faces = rekognitionFacade.detectFaces(request);
            if (awsMetrics != null) {
                awsMetrics.updateRekognitionImageUnits(1L);
            }
            // Convert faces to simple LabelsResult (each face as label "face")
            return new LabelsResult(faces.stream().map(f -> new LabelsResult.Label("face", f.confidence())).toList());
        } catch (SdkServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new NuxeoException("Failed to detect faces (DTO)", e);
        }
    }

    // Legacy synchronous DetectFacesResponse (AWS SDK) for providers still using AWS classes
    @Override
    public DetectFacesResponse detectFaces(ManagedBlob blob) {
        Image image = AWSHelper.getInstance().getImage(blob);
        DetectFacesRequest.Builder builder = DetectFacesRequest.builder().image(image);
        DetectFacesResponse response = rekognitionClient.detectFaces(builder.build());
        if (awsMetrics != null) {
            awsMetrics.updateRekognitionImageUnits(1L);
        }
        return response;
    }

    // Domain celebrity recognition -> returns LabelsResult
    @Override
    public LabelsResult recognizeCelebrities(ManagedBlob blob) {
        try {
            RekognitionRequest.DetectFaces request = createDetectFacesRequest(blob, false);
            List<RekognitionResult.Celebrity> celebs = rekognitionFacade.recognizeCelebrities(request);
            if (awsMetrics != null) {
                awsMetrics.updateRekognitionImageUnits(1L);
            }
            return new LabelsResult(
                    celebs.stream().map(c -> new LabelsResult.Label(c.name(), c.confidence())).toList());
        } catch (SdkServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new NuxeoException("Failed to recognize celebrities (DTO)", e);
        }
    }

    // Legacy synchronous AWS RecognizeCelebritiesResponse
    @Override
    public RecognizeCelebritiesResponse detectCelebrities(ManagedBlob blob) {
        Image image = AWSHelper.getInstance().getImage(blob);
        RecognizeCelebritiesRequest request = RecognizeCelebritiesRequest.builder().image(image).build();
        RecognizeCelebritiesResponse response = rekognitionClient.recognizeCelebrities(request);
        if (awsMetrics != null) {
            awsMetrics.updateRekognitionImageUnits(1L);
        }
        return response;
    }

    @Override
    public String startDetectFaces(ManagedBlob blob) {
        Video video = AWSHelper.getInstance().getVideo(blob);
        if (video == null) {
            throw new NuxeoException("Blob is not a video");
        }
        StartFaceDetectionRequest request = StartFaceDetectionRequest.builder().video(video).build();
        StartFaceDetectionResponse resp = rekognitionClient.startFaceDetection(request);
        return resp.jobId();
    }

    @Override
    public String startDetectCelebrities(ManagedBlob blob) {
        Video video = AWSHelper.getInstance().getVideo(blob);
        if (video == null) {
            throw new NuxeoException("Blob is not a video");
        }
        StartCelebrityRecognitionRequest request = StartCelebrityRecognitionRequest.builder().video(video).build();
        StartCelebrityRecognitionResponse resp = rekognitionClient.startCelebrityRecognition(request);
        return resp.jobId();
    }

    @Override
    public String startDetectUnsafeImages(ManagedBlob blob) {
        Video video = AWSHelper.getInstance().getVideo(blob);
        if (video == null) {
            throw new NuxeoException("Blob is not a video");
        }
        StartContentModerationRequest request = StartContentModerationRequest.builder().video(video).build();
        StartContentModerationResponse resp = rekognitionClient.startContentModeration(request);
        return resp.jobId();
    }

    @Override
    public String startVideoSegmentDetection(ManagedBlob blob, SegmentType segmentType) {
        Video video = AWSHelper.getInstance().getVideo(blob);
        if (video == null) {
            throw new NuxeoException("Blob is not a video");
        }
        StartSegmentDetectionRequest request = StartSegmentDetectionRequest.builder()
                                                                           .video(video)
                                                                           .segmentTypes(segmentType)
                                                                           .build();
        StartSegmentDetectionResponse resp = rekognitionClient.startSegmentDetection(request);
        return resp.jobId();
    }

    @Override
    public RekognitionClient getClient() {
        return rekognitionClient;
    }

    // Helper to create faces request for facade
    private RekognitionRequest.DetectFaces createDetectFacesRequest(ManagedBlob blob, boolean includeAttributes) {
        // Use AWSHelper to properly handle both S3 and direct blobs
        Image image = AWSHelper.getInstance().getImage(blob);
        if (image == null) {
            throw new NuxeoException("Unable to create image from blob: " + blob.getKey());
        }

        // Check if it's an S3 reference
        if (image.s3Object() != null) {
            return new RekognitionRequest.DetectFaces(
                image.s3Object().bucket(),
                image.s3Object().name(),
                includeAttributes
            );
        } else if (image.bytes() != null) {
            return new RekognitionRequest.DetectFaces(
                image.bytes().asByteArray(),
                includeAttributes
            );
        } else {
            throw new NuxeoException("Image has neither S3 reference nor bytes data");
        }
    }

    // Helper methods to create abstraction DTOs from ManagedBlob
    private RekognitionRequest.DetectLabels createDetectLabelsRequest(ManagedBlob blob, int maxLabels,
            float minConfidence) {
        // Use AWSHelper to properly handle both S3 and direct blobs
        Image image = AWSHelper.getInstance().getImage(blob);
        if (image == null) {
            throw new NuxeoException("Unable to create image from blob: " + blob.getKey());
        }

        // Check if it's an S3 reference
        if (image.s3Object() != null) {
            return new RekognitionRequest.DetectLabels(
                image.s3Object().bucket(),
                image.s3Object().name(),
                maxLabels,
                minConfidence
            );
        } else if (image.bytes() != null) {
            return new RekognitionRequest.DetectLabels(
                image.bytes().asByteArray(),
                maxLabels,
                minConfidence
            );
        } else {
            throw new NuxeoException("Image has neither S3 reference nor bytes data");
        }
    }

    private RekognitionRequest.DetectText createDetectTextRequest(ManagedBlob blob) {
        // Use AWSHelper to properly handle both S3 and direct blobs
        Image image = AWSHelper.getInstance().getImage(blob);
        if (image == null) {
            throw new NuxeoException("Unable to create image from blob: " + blob.getKey());
        }

        // Check if it's an S3 reference
        if (image.s3Object() != null) {
            return new RekognitionRequest.DetectText(
                image.s3Object().bucket(),
                image.s3Object().name()
            );
        } else if (image.bytes() != null) {
            return new RekognitionRequest.DetectText(
                image.bytes().asByteArray()
            );
        } else {
            throw new NuxeoException("Image has neither S3 reference nor bytes data");
        }
    }

    // Conversion methods to maintain backward compatibility with existing DTOs
    private LabelsResult convertToLabelsResult(List<RekognitionResult.Label> labels) {
        // Convert our abstraction DTOs back to existing LabelsResult format
        // This maintains backward compatibility with existing code
        return new LabelsResult(
                labels.stream().map(label -> new LabelsResult.Label(label.name(), label.confidence())).toList());
    }

    private LabelsResult convertModerationLabelsToLabelsResult(
            List<RekognitionResult.ModerationLabel> moderationLabels) {
        return new LabelsResult(moderationLabels.stream()
                                                .map(label -> new LabelsResult.Label(label.name(), label.confidence()))
                                                .toList());
    }

    private TextDetectionResult convertToTextDetectionResult(List<RekognitionResult.TextDetection> textDetections) {
        return new TextDetectionResult(
                textDetections.stream()
                              .map(detection -> new TextDetectionResult.TextDetection(detection.detectedText(),
                                      detection.type(), detection.confidence()))
                              .toList());
    }

    // ...existing code for other methods...
}
