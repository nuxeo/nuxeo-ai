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

    public static class DetectLabels {
        private final byte[] imageData;
        private final String s3Bucket;
        private final String s3Key;
        private final int maxLabels;
        private final float minConfidence;

        // Constructor for direct image data
        public DetectLabels(byte[] imageData, int maxLabels, float minConfidence) {
            this.imageData = imageData;
            this.s3Bucket = null;
            this.s3Key = null;
            this.maxLabels = maxLabels;
            this.minConfidence = minConfidence;
        }

        // Constructor for S3 reference
        public DetectLabels(String s3Bucket, String s3Key, int maxLabels, float minConfidence) {
            this.imageData = null;
            this.s3Bucket = s3Bucket;
            this.s3Key = s3Key;
            this.maxLabels = maxLabels;
            this.minConfidence = minConfidence;
        }

        public byte[] getImageData() { return imageData; }
        public String getS3Bucket() { return s3Bucket; }
        public String getS3Key() { return s3Key; }
        public int getMaxLabels() { return maxLabels; }
        public float getMinConfidence() { return minConfidence; }
        public boolean isS3Reference() { return s3Bucket != null && s3Key != null; }
    }

    public static class DetectFaces {
        private final byte[] imageData;
        private final String s3Bucket;
        private final String s3Key;
        private final boolean includeAttributes;

        public DetectFaces(byte[] imageData, boolean includeAttributes) {
            this.imageData = imageData;
            this.s3Bucket = null;
            this.s3Key = null;
            this.includeAttributes = includeAttributes;
        }

        public DetectFaces(String s3Bucket, String s3Key, boolean includeAttributes) {
            this.imageData = null;
            this.s3Bucket = s3Bucket;
            this.s3Key = s3Key;
            this.includeAttributes = includeAttributes;
        }

        public byte[] getImageData() { return imageData; }
        public String getS3Bucket() { return s3Bucket; }
        public String getS3Key() { return s3Key; }
        public boolean isIncludeAttributes() { return includeAttributes; }
        public boolean isS3Reference() { return s3Bucket != null && s3Key != null; }
    }

    public static class DetectText {
        private final byte[] imageData;
        private final String s3Bucket;
        private final String s3Key;

        public DetectText(byte[] imageData) {
            this.imageData = imageData;
            this.s3Bucket = null;
            this.s3Key = null;
        }

        public DetectText(String s3Bucket, String s3Key) {
            this.imageData = null;
            this.s3Bucket = s3Bucket;
            this.s3Key = s3Key;
        }

        public byte[] getImageData() { return imageData; }
        public String getS3Bucket() { return s3Bucket; }
        public String getS3Key() { return s3Key; }
        public boolean isS3Reference() { return s3Bucket != null && s3Key != null; }
    }
}
