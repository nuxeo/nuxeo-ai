/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.aws.dto;

import java.util.List;

/**
 * AWS SDK-independent result DTO for Textract operations.
 */
public class TextractResult {

    private final List<Block> blocks;
    private final String documentMetadata;
    private final String jobStatus;

    public TextractResult(List<Block> blocks, String documentMetadata, String jobStatus) {
        this.blocks = blocks;
        this.documentMetadata = documentMetadata;
        this.jobStatus = jobStatus;
    }

    public List<Block> getBlocks() { return blocks; }
    public String getDocumentMetadata() { return documentMetadata; }
    public String getJobStatus() { return jobStatus; }

    public static class Block {
        private final String blockType;
        private final String text;
        private final float confidence;
        private final BoundingBox boundingBox;

        public Block(String blockType, String text, float confidence, BoundingBox boundingBox) {
            this.blockType = blockType;
            this.text = text;
            this.confidence = confidence;
            this.boundingBox = boundingBox;
        }

        public String getBlockType() { return blockType; }
        public String getText() { return text; }
        public float getConfidence() { return confidence; }
        public BoundingBox getBoundingBox() { return boundingBox; }
    }

    public static class BoundingBox {
        private final float left, top, width, height;

        public BoundingBox(float left, float top, float width, float height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }

        public float getLeft() { return left; }
        public float getTop() { return top; }
        public float getWidth() { return width; }
        public float getHeight() { return height; }
    }

    @Override
    public String toString() {
        return "TextractResult{" +
                "blocks=" + (blocks != null ? blocks.size() : 0) +
                ", jobStatus='" + jobStatus + '\'' +
                '}';
    }
}
