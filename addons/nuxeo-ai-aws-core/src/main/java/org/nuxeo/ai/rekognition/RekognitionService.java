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

import org.nuxeo.ecm.core.blob.ManagedBlob;

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.model.Attribute;
import com.amazonaws.services.rekognition.model.DetectFacesResult;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.DetectModerationLabelsResult;
import com.amazonaws.services.rekognition.model.DetectTextResult;
import com.amazonaws.services.rekognition.model.FaceAttributes;
import com.amazonaws.services.rekognition.model.RecognizeCelebritiesResult;

/**
 * Works with AWS Rekognition
 */
public interface RekognitionService {

    String DETECT_SNS_TOPIC = "detect";

    /**
     * Detect labels for the provided blob
     */
    DetectLabelsResult detectLabels(ManagedBlob blob, int maxResults, float minConfidence);

    /**
     * Starts async detect of labels for the provided blob
     *
     * @param blob          a blob reference to a video
     * @param minConfidence min confidence to accept
     * @return JobId
     */
    String startDetectLabels(ManagedBlob blob, float minConfidence);

    /**
     * Detect text for the provided blob
     */
    DetectTextResult detectText(ManagedBlob blob);

    /**
     * Detect if the provided blob contains explicit or suggestive adult content.
     */
    DetectModerationLabelsResult detectUnsafeImages(ManagedBlob blob);

    /**
     * Starts async detect of explicit or suggestive adult content for
     *
     * @param blob reference to a video
     */
    String startDetectUnsafe(ManagedBlob blob);

    /**
     * Detect faces for the provided blob
     */
    DetectFacesResult detectFaces(ManagedBlob blob, Attribute... attributes);

    /**
     * Starts async detect of faces for the provided blob
     *
     * @param blob reference to a video
     */
    String startDetectFaces(ManagedBlob blob, FaceAttributes attributes);

    /**
     * Detect celebrity faces for the provided blob
     */
    RecognizeCelebritiesResult detectCelebrityFaces(ManagedBlob blob);

    /**
     * Starts async detect of celebrity faces for the provided blob
     *
     * @param blob reference to a video
     */
    String startDetectCelebrityFaces(ManagedBlob blob);

    /**
     * @return AWS Rekognition client
     */
    AmazonRekognition getClient();
}
