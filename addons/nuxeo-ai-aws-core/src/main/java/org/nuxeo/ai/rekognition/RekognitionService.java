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

import org.nuxeo.ai.aws.dto.LabelsResult;
import org.nuxeo.ai.aws.dto.TextDetectionResult;
import org.nuxeo.ecm.core.blob.ManagedBlob;

import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.DetectFacesResponse;
import software.amazon.awssdk.services.rekognition.model.RecognizeCelebritiesResponse;
import software.amazon.awssdk.services.rekognition.model.SegmentType;

/**
 * Works with AWS Rekognition - Now using domain DTOs instead of AWS SDK models This interface is completely independent
 * of AWS SDK implementation details
 */
public interface RekognitionService {

    String DETECT_SNS_TOPIC = "detect";

    /**
     * Detect labels for the provided blob
     */
    LabelsResult detectLabels(ManagedBlob blob, int maxResults, float minConfidence);

    /**
     * Starts async detect of labels for the provided blob
     *
     * @param blob a blob reference to a video
     * @param minConfidence min confidence to accept
     * @return JobId
     */
    String startLabelDetection(ManagedBlob blob, float minConfidence);

    /**
     * Detect unsafe images using moderation labels
     */
    LabelsResult detectModerationLabels(ManagedBlob blob, float minConfidence);

    /**
     * Detect text in images
     */
    TextDetectionResult detectText(ManagedBlob blob);

    /**
     * Detect faces in images
     */
    LabelsResult detectFaces(ManagedBlob blob, Collection<String> attributes);

    /**
     * Recognize celebrities in images
     */
    LabelsResult recognizeCelebrities(ManagedBlob blob);

    // -----------------------------------------------------------------------
    // Legacy / AWS-specific methods kept for backward compatibility with
    // existing enrichment providers. These will be phased out once providers
    // are migrated to the abstraction DTOs entirely.
    // -----------------------------------------------------------------------

    /** Backward compatible alias for startLabelDetection */
    default String startDetectLabels(ManagedBlob blob, float minConfidence) {
        return startLabelDetection(blob, minConfidence);
    }

    /** Start async face detection on a video blob */
    String startDetectFaces(ManagedBlob blob);

    /** Start async celebrity recognition on a video blob */
    String startDetectCelebrities(ManagedBlob blob);

    /** Start async unsafe image (content moderation) detection on a video blob */
    String startDetectUnsafeImages(ManagedBlob blob);

    /** Start async segment detection (SHOT / TECHNICAL_CUE) on a video blob */
    String startVideoSegmentDetection(ManagedBlob blob, SegmentType segmentType);

    /** Synchronous face detection returning AWS SDK model (image use-case) */
    DetectFacesResponse detectFaces(ManagedBlob blob);

    /** Synchronous celebrity recognition returning AWS SDK model */
    RecognizeCelebritiesResponse detectCelebrities(ManagedBlob blob);

    /** Expose underlying client for legacy async polling code */
    RekognitionClient getClient();
}
