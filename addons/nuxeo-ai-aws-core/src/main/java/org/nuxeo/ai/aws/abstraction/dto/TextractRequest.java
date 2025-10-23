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

import java.util.List;

/**
 * AWS SDK-independent request DTOs for Textract service.
 */
public class TextractRequest {

    public static record DetectDocumentText(byte[] documentData, String s3Bucket, String s3Key) {
        public DetectDocumentText(byte[] documentData) { this(documentData, null, null); }
        public DetectDocumentText(String s3Bucket, String s3Key) { this(null, s3Bucket, s3Key); }
        public byte[] getDocumentData() { return documentData; }
        public String getS3Bucket() { return s3Bucket; }
        public String getS3Key() { return s3Key; }
        public boolean isS3Reference() { return s3Bucket != null && s3Key != null; }
    }

    public static record AnalyzeDocument(byte[] documentData, String s3Bucket, String s3Key, List<String> featureTypes) {
        public AnalyzeDocument(byte[] documentData, List<String> featureTypes) { this(documentData, null, null, featureTypes); }
        public AnalyzeDocument(String s3Bucket, String s3Key, List<String> featureTypes) { this(null, s3Bucket, s3Key, featureTypes); }
        public byte[] getDocumentData() { return documentData; }
        public String getS3Bucket() { return s3Bucket; }
        public String getS3Key() { return s3Key; }
        public List<String> getFeatureTypes() { return featureTypes; }
        public boolean isS3Reference() { return s3Bucket != null && s3Key != null; }
    }
}
