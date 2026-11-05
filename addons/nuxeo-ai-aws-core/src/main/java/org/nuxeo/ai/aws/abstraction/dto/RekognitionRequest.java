/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.aws.abstraction.dto;

/**
 * AWS SDK-independent request DTOs for Rekognition service.
 */
public class RekognitionRequest {

    public static record DetectLabels(byte[] imageData, String s3Bucket, String s3Key, String s3Version, int maxLabels,
            float minConfidence) {
        // convenience constructors preserving old API
        public DetectLabels(byte[] imageData, int maxLabels, float minConfidence) {
            this(imageData, null, null, null, maxLabels, minConfidence);
        }

        public DetectLabels(String s3Bucket, String s3Key, int maxLabels, float minConfidence) {
            this(s3Bucket, s3Key, null, maxLabels, minConfidence);
        }

        public DetectLabels(String s3Bucket, String s3Key, String s3Version, int maxLabels, float minConfidence) {
            this(null, s3Bucket, s3Key, s3Version, maxLabels, minConfidence);
        }

        public boolean isS3Reference() {
            return s3Bucket != null && s3Key != null;
        }
    }

    public static record DetectFaces(byte[] imageData, String s3Bucket, String s3Key, String s3Version,
            boolean includeAttributes) {
        public DetectFaces(byte[] imageData, boolean includeAttributes) {
            this(imageData, null, null, null, includeAttributes);
        }

        public DetectFaces(String s3Bucket, String s3Key, boolean includeAttributes) {
            this(s3Bucket, s3Key, null, includeAttributes);
        }

        public DetectFaces(String s3Bucket, String s3Key, String s3Version, boolean includeAttributes) {
            this(null, s3Bucket, s3Key, s3Version, includeAttributes);
        }

        public boolean isS3Reference() {
            return s3Bucket != null && s3Key != null;
        }
    }

    public static record DetectText(byte[] imageData, String s3Bucket, String s3Key, String s3Version) {
        public DetectText(byte[] imageData) {
            this(imageData, null, null, null);
        }

        public DetectText(String s3Bucket, String s3Key) {
            this(s3Bucket, s3Key, null);
        }

        public DetectText(String s3Bucket, String s3Key, String s3Version) {
            this(null, s3Bucket, s3Key, s3Version);
        }

        public boolean isS3Reference() {
            return s3Bucket != null && s3Key != null;
        }
    }
}
