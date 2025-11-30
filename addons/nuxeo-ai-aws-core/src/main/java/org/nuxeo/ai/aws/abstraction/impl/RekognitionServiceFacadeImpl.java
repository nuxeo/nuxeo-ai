/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.aws.abstraction.impl;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.aws.AWSClientFactory;
import org.nuxeo.ai.aws.abstraction.RekognitionServiceFacade;
import org.nuxeo.ai.aws.abstraction.dto.RekognitionRequest;
import org.nuxeo.ai.aws.dto.RekognitionResult;
import org.nuxeo.ai.aws.mapper.RekognitionMapper;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

/**
 * Implementation of RekognitionServiceFacade that isolates ALL AWS SDK dependencies. This is the ONLY class that
 * imports AWS SDK classes for Rekognition operations.
 */
public class RekognitionServiceFacadeImpl extends DefaultComponent implements RekognitionServiceFacade {

    private static final Log log = LogFactory.getLog(RekognitionServiceFacadeImpl.class);

    @Override
    public List<RekognitionResult.Label> detectLabels(RekognitionRequest.DetectLabels request) {
        try {
            AWSClientFactory clientFactory = Framework.getService(AWSClientFactory.class);
            RekognitionClient client = clientFactory.getRekognitionClient();

            Image image = buildImage(request.imageData(), request.s3Bucket(), request.s3Key());

            DetectLabelsRequest awsRequest = DetectLabelsRequest.builder()
                                                                .image(image)
                                                                .maxLabels(request.maxLabels())
                                                                .minConfidence(request.minConfidence())
                                                                .build();

            var response = client.detectLabels(awsRequest);
            return RekognitionMapper.mapToLabels(response.labels());
        } catch (Exception e) {
            log.error("Error detecting labels", e);
            throw new RuntimeException("Failed to detect labels", e);
        }
    }

    @Override
    public List<RekognitionResult.Face> detectFaces(RekognitionRequest.DetectFaces request) {
        try {
            AWSClientFactory clientFactory = Framework.getService(AWSClientFactory.class);
            RekognitionClient client = clientFactory.getRekognitionClient();

            Image image = buildImage(request.imageData(), request.s3Bucket(), request.s3Key());

            DetectFacesRequest.Builder awsRequestBuilder = DetectFacesRequest.builder().image(image);

            if (request.includeAttributes()) {
                awsRequestBuilder.attributes(Attribute.ALL);
            }

            var response = client.detectFaces(awsRequestBuilder.build());
            return RekognitionMapper.mapToFaces(response.faceDetails());
        } catch (Exception e) {
            log.error("Error detecting faces", e);
            throw new RuntimeException("Failed to detect faces", e);
        }
    }

    @Override
    public List<RekognitionResult.TextDetection> detectText(RekognitionRequest.DetectText request) {
        try {
            AWSClientFactory clientFactory = Framework.getService(AWSClientFactory.class);
            RekognitionClient client = clientFactory.getRekognitionClient();

            Image image = buildImage(request.imageData(), request.s3Bucket(), request.s3Key());

            DetectTextRequest awsRequest = DetectTextRequest.builder().image(image).build();

            var response = client.detectText(awsRequest);
            return RekognitionMapper.mapToTextDetections(response.textDetections());
        } catch (Exception e) {
            log.error("Error detecting text", e);
            throw new RuntimeException("Failed to detect text", e);
        }
    }

    @Override
    public List<RekognitionResult.ModerationLabel> detectModerationLabels(RekognitionRequest.DetectLabels request) {
        try {
            AWSClientFactory clientFactory = Framework.getService(AWSClientFactory.class);
            RekognitionClient client = clientFactory.getRekognitionClient();

            Image image = buildImage(request.imageData(), request.s3Bucket(), request.s3Key());

            DetectModerationLabelsRequest awsRequest = DetectModerationLabelsRequest.builder()
                                                                                    .image(image)
                                                                                    .minConfidence(
                                                                                            request.minConfidence())
                                                                                    .build();

            var response = client.detectModerationLabels(awsRequest);
            return RekognitionMapper.mapToModerationLabels(response.moderationLabels());
        } catch (Exception e) {
            log.error("Error detecting moderation labels", e);
            throw new RuntimeException("Failed to detect moderation labels", e);
        }
    }

    @Override
    public List<RekognitionResult.Celebrity> recognizeCelebrities(RekognitionRequest.DetectFaces request) {
        try {
            AWSClientFactory clientFactory = Framework.getService(AWSClientFactory.class);
            RekognitionClient client = clientFactory.getRekognitionClient();

            Image image = buildImage(request.imageData(), request.s3Bucket(), request.s3Key());

            RecognizeCelebritiesRequest awsRequest = RecognizeCelebritiesRequest.builder().image(image).build();

            var response = client.recognizeCelebrities(awsRequest);
            return RekognitionMapper.mapToCelebrities(response.celebrityFaces());
        } catch (Exception e) {
            log.error("Error recognizing celebrities", e);
            throw new RuntimeException("Failed to recognize celebrities", e);
        }
    }

    /**
     * Helper method to build AWS SDK Image object from our abstraction
     */
    private Image buildImage(byte[] imageData, String s3Bucket, String s3Key) {
        if (imageData != null) {
            return Image.builder().bytes(SdkBytes.fromByteArray(imageData)).build();
        } else if (s3Bucket != null && s3Key != null) {
            S3Object s3Object = S3Object.builder().bucket(s3Bucket).name(s3Key).build();
            return Image.builder().s3Object(s3Object).build();
        } else {
            throw new IllegalArgumentException("Either image data or S3 reference must be provided");
        }
    }
}
