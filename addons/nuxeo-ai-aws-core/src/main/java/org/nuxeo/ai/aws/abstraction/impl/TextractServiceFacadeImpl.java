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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.aws.AWSClientFactory;
import org.nuxeo.ai.aws.abstraction.TextractServiceFacade;
import org.nuxeo.ai.aws.abstraction.dto.TextractRequest;
import org.nuxeo.ai.aws.dto.TextractResult;
import org.nuxeo.ai.aws.mapper.TextractMapper;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of TextractServiceFacade that isolates ALL AWS SDK dependencies.
 * This is the ONLY class that imports AWS SDK classes for Textract operations.
 */
public class TextractServiceFacadeImpl extends DefaultComponent implements TextractServiceFacade {

    private static final Log log = LogFactory.getLog(TextractServiceFacadeImpl.class);

    @Override
    public TextractResult detectDocumentText(TextractRequest.DetectDocumentText request) {
        try {
            AWSClientFactory clientFactory = Framework.getService(AWSClientFactory.class);
            TextractClient client = clientFactory.getTextractClient();

            Document document = buildDocument(request.getDocumentData(), request.getS3Bucket(), request.getS3Key());

            DetectDocumentTextRequest awsRequest = DetectDocumentTextRequest.builder()
                    .document(document)
                    .build();

            var response = client.detectDocumentText(awsRequest);
            return TextractMapper.mapToTextractResult(response);
        } catch (Exception e) {
            log.error("Error detecting document text", e);
            throw new RuntimeException("Failed to detect document text", e);
        }
    }

    @Override
    public TextractResult analyzeDocument(TextractRequest.AnalyzeDocument request) {
        try {
            AWSClientFactory clientFactory = Framework.getService(AWSClientFactory.class);
            TextractClient client = clientFactory.getTextractClient();

            Document document = buildDocument(request.getDocumentData(), request.getS3Bucket(), request.getS3Key());

            List<FeatureType> featureTypes = request.getFeatureTypes().stream()
                    .map(FeatureType::fromValue)
                    .collect(Collectors.toList());

            AnalyzeDocumentRequest awsRequest = AnalyzeDocumentRequest.builder()
                    .document(document)
                    .featureTypes(featureTypes)
                    .build();

            var response = client.analyzeDocument(awsRequest);
            return TextractMapper.mapToTextractResult(response);
        } catch (Exception e) {
            log.error("Error analyzing document", e);
            throw new RuntimeException("Failed to analyze document", e);
        }
    }

    /**
     * Helper method to build AWS SDK Document object from our abstraction
     */
    private Document buildDocument(byte[] documentData, String s3Bucket, String s3Key) {
        if (documentData != null) {
            return Document.builder()
                    .bytes(SdkBytes.fromByteArray(documentData))
                    .build();
        } else if (s3Bucket != null && s3Key != null) {
            S3Object s3Object = S3Object.builder()
                    .bucket(s3Bucket)
                    .name(s3Key)
                    .build();
            return Document.builder()
                    .s3Object(s3Object)
                    .build();
        } else {
            throw new IllegalArgumentException("Either document data or S3 reference must be provided");
        }
    }
}
