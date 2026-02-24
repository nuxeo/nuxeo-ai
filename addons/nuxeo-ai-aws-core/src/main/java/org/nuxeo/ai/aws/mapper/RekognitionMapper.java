/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.aws.mapper;

import java.util.List;
import java.util.stream.Collectors;

import org.nuxeo.ai.aws.dto.RekognitionResult;
import software.amazon.awssdk.services.rekognition.model.*;

/**
 * Maps AWS SDK Rekognition responses to our abstraction layer DTOs. This is one of the few classes that imports AWS SDK
 * classes.
 */
public class RekognitionMapper {

    private RekognitionMapper() {
        // Utility class, hide constructor
    }

    public static List<RekognitionResult.Label> mapToLabels(List<Label> awsLabels) {
        return awsLabels.stream().map(RekognitionMapper::mapToLabel).collect(Collectors.toList());
    }

    public static RekognitionResult.Label mapToLabel(Label awsLabel) {
        List<String> categories = awsLabel.categories()
                                          .stream()
                                          .map(category -> category.name())
                                          .collect(Collectors.toList());

        return new RekognitionResult.Label(awsLabel.name(), awsLabel.confidence(), categories);
    }

    public static List<RekognitionResult.Face> mapToFaces(List<FaceDetail> awsFaces) {
        return awsFaces.stream().map(RekognitionMapper::mapToFace).collect(Collectors.toList());
    }

    public static RekognitionResult.Face mapToFace(FaceDetail awsFace) {
        RekognitionResult.BoundingBox boundingBox = mapToBoundingBox(awsFace.boundingBox());
        RekognitionResult.FaceAttributes attributes = mapToFaceAttributes(awsFace);

        return new RekognitionResult.Face(awsFace.confidence(), boundingBox, attributes);
    }

    public static RekognitionResult.BoundingBox mapToBoundingBox(BoundingBox awsBoundingBox) {
        return new RekognitionResult.BoundingBox(awsBoundingBox.left(), awsBoundingBox.top(), awsBoundingBox.width(),
                awsBoundingBox.height());
    }

    public static RekognitionResult.FaceAttributes mapToFaceAttributes(FaceDetail awsFace) {
        Integer estimatedAge = null;
        if (awsFace.ageRange() != null) {
            estimatedAge = (awsFace.ageRange().low() + awsFace.ageRange().high()) / 2;
        }

        String gender = awsFace.gender() != null ? awsFace.gender().value().toString() : null;
        boolean smile = awsFace.smile() != null && awsFace.smile().value();
        boolean eyeglasses = awsFace.eyeglasses() != null && awsFace.eyeglasses().value();
        boolean sunglasses = awsFace.sunglasses() != null && awsFace.sunglasses().value();

        return new RekognitionResult.FaceAttributes(estimatedAge, gender, smile, eyeglasses, sunglasses);
    }

    public static List<RekognitionResult.TextDetection> mapToTextDetections(List<TextDetection> awsTextDetections) {
        return awsTextDetections.stream().map(RekognitionMapper::mapToTextDetection).collect(Collectors.toList());
    }

    public static RekognitionResult.TextDetection mapToTextDetection(TextDetection awsTextDetection) {
        RekognitionResult.BoundingBox boundingBox = null;
        if (awsTextDetection.geometry() != null && awsTextDetection.geometry().boundingBox() != null) {
            boundingBox = mapToBoundingBox(awsTextDetection.geometry().boundingBox());
        }

        return new RekognitionResult.TextDetection(awsTextDetection.detectedText(), awsTextDetection.type().toString(),
                awsTextDetection.confidence(), boundingBox);
    }

    public static List<RekognitionResult.ModerationLabel> mapToModerationLabels(List<ModerationLabel> awsLabels) {
        return awsLabels.stream().map(RekognitionMapper::mapToModerationLabel).collect(Collectors.toList());
    }

    public static RekognitionResult.ModerationLabel mapToModerationLabel(ModerationLabel awsLabel) {
        return new RekognitionResult.ModerationLabel(awsLabel.name(), awsLabel.confidence(), awsLabel.parentName());
    }

    public static List<RekognitionResult.Celebrity> mapToCelebrities(List<Celebrity> awsCelebrities) {
        return awsCelebrities.stream().map(RekognitionMapper::mapToCelebrity).collect(Collectors.toList());
    }

    public static RekognitionResult.Celebrity mapToCelebrity(Celebrity awsCelebrity) {
        RekognitionResult.BoundingBox boundingBox = null;
        if (awsCelebrity.face() != null && awsCelebrity.face().boundingBox() != null) {
            boundingBox = mapToBoundingBox(awsCelebrity.face().boundingBox());
        }

        return new RekognitionResult.Celebrity(awsCelebrity.name(), awsCelebrity.matchConfidence(), awsCelebrity.urls(),
                boundingBox);
    }
}
