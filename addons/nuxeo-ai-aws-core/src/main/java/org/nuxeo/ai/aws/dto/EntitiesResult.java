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
 * AWS SDK-independent result DTO for entity detection operations.
 */
public class EntitiesResult {

    private final List<Entity> entities;

    public EntitiesResult(List<Entity> entities) {
        this.entities = entities;
    }

    public List<Entity> getEntities() { return entities; }

    public static class Entity {
        private final String text;
        private final String type;
        private final float score;
        private final int beginOffset;
        private final int endOffset;

        public Entity(String text, String type, float score, int beginOffset, int endOffset) {
            this.text = text;
            this.type = type;
            this.score = score;
            this.beginOffset = beginOffset;
            this.endOffset = endOffset;
        }

        public String getText() { return text; }
        public String getType() { return type; }
        public float getScore() { return score; }
        public int getBeginOffset() { return beginOffset; }
        public int getEndOffset() { return endOffset; }

        @Override
        public String toString() {
            return "Entity{" +
                    "text='" + text + '\'' +
                    ", type='" + type + '\'' +
                    ", score=" + score +
                    ", beginOffset=" + beginOffset +
                    ", endOffset=" + endOffset +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "EntitiesResult{" +
                "entities=" + entities +
                '}';
    }
}
