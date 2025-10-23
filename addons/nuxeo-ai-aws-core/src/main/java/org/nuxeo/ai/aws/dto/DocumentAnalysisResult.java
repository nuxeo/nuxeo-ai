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
public record DocumentAnalysisResult(List<Block> blocks) {
    public List<Block> getBlocks() { return blocks; }

    // equals/hashCode from record sufficient
    public static record Block(String blockType, Float confidence, String text, BoundingBox boundingBox, List<String> relationships) {
        public String getBlockType() { return blockType; }
        public Float getConfidence() { return confidence; }
        public String getText() { return text; }
        public BoundingBox getBoundingBox() { return boundingBox; }
        public List<String> getRelationships() { return relationships; }
    }
    public static record BoundingBox(Float width, Float height, Float left, Float top) {
        public Float getWidth() { return width; }
        public Float getHeight() { return height; }
        public Float getLeft() { return left; }
        public Float getTop() { return top; }
    }
}
