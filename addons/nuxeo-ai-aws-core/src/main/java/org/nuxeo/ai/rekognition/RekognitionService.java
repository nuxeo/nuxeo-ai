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

import software.amazon.awssdk.services.rekognition.model.SegmentType;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.Attribute;
import software.amazon.awssdk.services.rekognition.model.DetectFacesResponse;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.DetectModerationLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.DetectTextResponse;
import software.amazon.awssdk.services.rekognition.model.FaceAttributes;
import software.amazon.awssdk.services.rekognition.model.RecognizeCelebritiesResponse;

/**
 * Works with AWS Rekognition
 */
public interface RekognitionService {

    String DETECT_SNS_TOPIC = "detect";

    /**
     * Detect labels for the provided blob
     */
    DetectLabelsResponse detectLabels(ManagedBlob blob, int maxResults, float minConfidence);

    /**
     * Starts async detect of labels for the provided blob
     *
     * @param blob a blob reference to a video
     * @param minConfidence min confidence to accept
     * @return JobId
     */
    String startDetectLabels(ManagedBlob blob, float minConfidence);

    /**
     * Detect text for the provided blob
     */
    DetectTextResponse detectText(ManagedBlob blob);

    /**
     * Detect unsafe content for the provided blob
     */
    DetectModerationLabelsResponse detectUnsafeImages(ManagedBlob blob);

    /**
     * Starts async detect of unsafe content for the provided blob
     *
     * @param blob a blob reference to a video
     * @return JobId
     */
    String startDetectUnsafeImages(ManagedBlob blob);

    /**
     * Detect faces for the provided blob
     */
    DetectFacesResponse detectFaces(ManagedBlob blob);

    /**
     * Starts async detect of faces for the provided blob
     *
     * @param blob a blob reference to a video
     * @return JobId
     */
    String startDetectFaces(ManagedBlob blob);

    /**
     * Detect celebrities
     */
    RecognizeCelebritiesResponse detectCelebrities(ManagedBlob blob);

    /**
     * Starts async detect of celebrities for the provided blob
     *
     * @param blob a blob reference to a video
     * @return JobId
     */
    String startDetectCelebrities(ManagedBlob blob);

    /**
     * Starts async detect of video segments for the provided blob
     *
     * @param blob a blob reference to a video
     * @param segmentType segment type to detect
     * @return JobId
     */
    String startVideoSegmentDetection(ManagedBlob blob, SegmentType segmentType);

    /**
     * @return the AWS client
     */
    public RekognitionClient getClient();
}
