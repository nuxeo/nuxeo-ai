/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Gethin James
 */
package org.nuxeo.runtime.stream.pipes.types;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.blob.BlobMeta;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A representation of DocumentModel as a record data in a stream.
 */
public class DocStream implements HasKey {

    public final String id;
    public final String parentId;
    public final String primaryType;
    public final Set<String> facets;

    private final Map<String, BlobMeta> blobs = new HashMap<>();
    private final Map<String, String> text = new HashMap<>();
    private final Map<String, Map<String, String>> properties = new HashMap<>();

    @JsonCreator
    public DocStream(@JsonProperty("id") String id,
                     @JsonProperty("parentId") String parentId,
                     @JsonProperty("primaryType") String primaryType,
                     @JsonProperty("facets") Set<String> facets) {
        this.id = id;
        this.parentId = parentId;
        this.primaryType = primaryType;
        this.facets = Collections.unmodifiableSet(facets);
    }

    public Map<String, BlobMeta> getBlobs() {
        return blobs;
    }

    public Map<String, String> getText() {
        return text;
    }

    public Map<String, Map<String, String>> getProperties() {
        return properties;
    }

    @Override
    @JsonIgnore
    public String getKey() {
        return "P-" + parentId;
    }
}
