/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.aws.abstraction;

import org.nuxeo.ai.aws.abstraction.dto.RekognitionRequest;
import org.nuxeo.ai.aws.dto.RekognitionResult;

import java.util.List;

/**
 * AWS SDK-independent facade for Rekognition operations.
 */
public interface RekognitionServiceFacade {

    /**
     * Detect labels in image
     */
    List<RekognitionResult.Label> detectLabels(RekognitionRequest.DetectLabels request);

    /**
     * Detect faces in image
     */
    List<RekognitionResult.Face> detectFaces(RekognitionRequest.DetectFaces request);

    /**
     * Detect text in image
     */
    List<RekognitionResult.TextDetection> detectText(RekognitionRequest.DetectText request);

    /**
     * Detect moderation labels in image
     */
    List<RekognitionResult.ModerationLabel> detectModerationLabels(RekognitionRequest.DetectLabels request);

    /**
     * Recognize celebrities in image
     */
    List<RekognitionResult.Celebrity> recognizeCelebrities(RekognitionRequest.DetectFaces request);
}
