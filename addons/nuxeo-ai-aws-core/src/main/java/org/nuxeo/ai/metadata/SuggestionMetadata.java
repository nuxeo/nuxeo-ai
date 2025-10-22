/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.metadata;

/**
 * Minimal stub implementation of SuggestionMetadata for compilation.
 */
public class SuggestionMetadata {

    private final String key;
    private final Object value;
    private final float confidence;

    public SuggestionMetadata(String key, Object value, float confidence) {
        this.key = key;
        this.value = value;
        this.confidence = confidence;
    }

    public String getKey() { return key; }
    public Object getValue() { return value; }
    public float getConfidence() { return confidence; }
}
