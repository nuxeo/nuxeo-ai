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
public record TextractResult(List<Block> blocks, String documentMetadata, String jobStatus) {
    public List<Block> getBlocks() { return blocks; }
    public String getDocumentMetadata() { return documentMetadata; }
    public String getJobStatus() { return jobStatus; }
    public static record Block(String blockType, String text, float confidence, BoundingBox boundingBox) {
        public String getBlockType() { return blockType; }
        public String getText() { return text; }
        public float getConfidence() { return confidence; }
        public BoundingBox getBoundingBox() { return boundingBox; }
    }
    public static record BoundingBox(float left, float top, float width, float height) {
        public float getLeft() { return left; }
        public float getTop() { return top; }
        public float getWidth() { return width; }
        public float getHeight() { return height; }
    }
    @Override public String toString() { return "TextractResult{" + "blocks=" + (blocks != null ? blocks.size() : 0) + ", jobStatus='" + jobStatus + '\'' + '}'; }
}
