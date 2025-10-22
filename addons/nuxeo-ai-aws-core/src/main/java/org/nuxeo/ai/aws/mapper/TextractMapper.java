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

import org.nuxeo.ai.aws.dto.TextractResult;
import software.amazon.awssdk.services.textract.model.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for AWS Textract service responses.
 */
public class TextractMapper {

    /**
     * Map AWS SDK DetectDocumentTextResponse to our abstraction DTO
     */
    public static TextractResult mapToTextractResult(DetectDocumentTextResponse response) {
        List<TextractResult.Block> blocks = response.blocks().stream()
                .map(TextractMapper::mapToBlock)
                .collect(Collectors.toList());

        return new TextractResult(blocks, "Document processed", "COMPLETED");
    }

    /**
     * Map AWS SDK AnalyzeDocumentResponse to our abstraction DTO
     */
    public static TextractResult mapToTextractResult(AnalyzeDocumentResponse response) {
        List<TextractResult.Block> blocks = response.blocks().stream()
                .map(TextractMapper::mapToBlock)
                .collect(Collectors.toList());

        return new TextractResult(blocks, "Document analyzed", "COMPLETED");
    }

    /**
     * Map AWS SDK DetectDocumentTextResponse to our abstraction DTO (legacy method)
     */
    public static TextractResult mapDetectDocumentTextResponse(DetectDocumentTextResponse response) {
        return mapToTextractResult(response);
    }

    /**
     * Map AWS SDK AnalyzeDocumentResponse to our abstraction DTO (legacy method)
     */
    public static TextractResult mapAnalyzeDocumentResponse(AnalyzeDocumentResponse response) {
        return mapToTextractResult(response);
    }

    /**
     * Map AWS SDK Block to our abstraction Block
     */
    private static TextractResult.Block mapToBlock(Block awsBlock) {
        TextractResult.BoundingBox boundingBox = null;
        if (awsBlock.geometry() != null && awsBlock.geometry().boundingBox() != null) {
            BoundingBox awsBoundingBox = awsBlock.geometry().boundingBox();
            boundingBox = new TextractResult.BoundingBox(
                    awsBoundingBox.left(),
                    awsBoundingBox.top(),
                    awsBoundingBox.width(),
                    awsBoundingBox.height()
            );
        }

        return new TextractResult.Block(
                awsBlock.blockType() != null ? awsBlock.blockType().toString() : "UNKNOWN",
                awsBlock.text() != null ? awsBlock.text() : "",
                awsBlock.confidence() != null ? awsBlock.confidence() : 0.0f,
                boundingBox
        );
    }
}
