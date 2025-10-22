/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.ai.aws.dto;

import java.util.List;
import java.util.Objects;

/**
 * Domain-specific DTO for Textract document analysis results.
 */
public class DocumentAnalysisResult {

    private final List<Block> blocks;

    public DocumentAnalysisResult(List<Block> blocks) {
        this.blocks = blocks;
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentAnalysisResult that = (DocumentAnalysisResult) o;
        return Objects.equals(blocks, that.blocks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blocks);
    }

    /**
     * Represents a block of text or data in the document
     */
    public static class Block {
        private final String blockType;
        private final Float confidence;
        private final String text;
        private final BoundingBox boundingBox;
        private final List<String> relationships;

        public Block(String blockType, Float confidence, String text, BoundingBox boundingBox, List<String> relationships) {
            this.blockType = blockType;
            this.confidence = confidence;
            this.text = text;
            this.boundingBox = boundingBox;
            this.relationships = relationships;
        }

        public String getBlockType() {
            return blockType;
        }

        public Float getConfidence() {
            return confidence;
        }

        public String getText() {
            return text;
        }

        public BoundingBox getBoundingBox() {
            return boundingBox;
        }

        public List<String> getRelationships() {
            return relationships;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Block block = (Block) o;
            return Objects.equals(blockType, block.blockType) &&
                   Objects.equals(confidence, block.confidence) &&
                   Objects.equals(text, block.text) &&
                   Objects.equals(boundingBox, block.boundingBox) &&
                   Objects.equals(relationships, block.relationships);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockType, confidence, text, boundingBox, relationships);
        }
    }

    /**
     * Represents a bounding box for text blocks
     */
    public static class BoundingBox {
        private final Float width;
        private final Float height;
        private final Float left;
        private final Float top;

        public BoundingBox(Float width, Float height, Float left, Float top) {
            this.width = width;
            this.height = height;
            this.left = left;
            this.top = top;
        }

        public Float getWidth() {
            return width;
        }

        public Float getHeight() {
            return height;
        }

        public Float getLeft() {
            return left;
        }

        public Float getTop() {
            return top;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BoundingBox that = (BoundingBox) o;
            return Objects.equals(width, that.width) &&
                   Objects.equals(height, that.height) &&
                   Objects.equals(left, that.left) &&
                   Objects.equals(top, that.top);
        }

        @Override
        public int hashCode() {
            return Objects.hash(width, height, left, top);
        }
    }
}
