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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.blob.BlobMeta;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A POJO representation of BlobTextStream.avsc used as a record data in a stream.
 */
public class BlobTextStream implements Partitionable {

    private String id;
    private String parentId;
    private String primaryType;
    private Set<String> facets;

    private String textId;
    private String text;
    private String blobId;
    private BlobMeta blob;

    private final Map<String, Map<String, String>> properties = new HashMap<>();

    public BlobTextStream() {
    }

    public BlobTextStream(String id, String parentId, String primaryType, Set<String> facets) {
        this.id = id;
        this.parentId = parentId;
        this.primaryType = primaryType;
        this.facets = facets;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getPrimaryType() {
        return primaryType;
    }

    public void setPrimaryType(String primaryType) {
        this.primaryType = primaryType;
    }

    public Set<String> getFacets() {
        return facets;
    }

    public void setFacets(Set<String> facets) {
        this.facets = facets;
    }

    public String getTextId() {
        return textId;
    }

    public void setTextId(String textId) {
        this.textId = textId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getBlobId() {
        return blobId;
    }

    public void setBlobId(String blobId) {
        this.blobId = blobId;
    }

    public BlobMeta getBlob() {
        return blob;
    }

    public void setBlob(BlobMeta blob) {
        this.blob = blob;
    }

    public Map<String, Map<String, String>> getProperties() {
        return properties;
    }

    @Override
    @JsonIgnore
    public String getKey() {
        return getId();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("BlobTextStream{");
        sb.append("id='").append(id).append('\'');
        sb.append(", parentId='").append(parentId).append('\'');
        sb.append(", primaryType='").append(primaryType).append('\'');
        sb.append(", facets=").append(facets);
        sb.append(", textId='").append(textId).append('\'');
        sb.append(", text='").append(text).append('\'');
        sb.append(", blobId='").append(blobId).append('\'');
        sb.append(", blob=").append(blob);
        sb.append(", properties=").append(properties);
        sb.append('}');
        return sb.toString();
    }
}
