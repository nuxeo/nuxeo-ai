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
 *
 * The main subject of this class is usually either a blob or a piece of text (not a Nuxeo Document).
 * If a blob is used then "blobXPath" is the XPath of the property that held the blob, e.g. "file:content"
 * If text then "textXPath" is the XPath of the property that held the text, e.g. "dc:title"
 *
 * Additional properties can be held in the "properties" map.
 */
public class BlobTextStream implements Partitionable {

    private String id;
    private String parentId;
    private String primaryType;
    private Set<String> facets;

    private String textXPath;
    private String text;
    private String blobXPath;
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

    /**
     * The XPath of the property used for this text data
     * @return a valid Nuxeo property XPath
     */
    public String getTextXPath() {
        return textXPath;
    }

    public void setTextXPath(String textXPath) {
        this.textXPath = textXPath;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    /**
     * The XPath of the property used for this blob data
     * @return a valid Nuxeo property XPath
     */
    public String getBlobXPath() {
        return blobXPath;
    }

    public void setBlobXPath(String blobXPath) {
        this.blobXPath = blobXPath;
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
        sb.append(", textXPath='").append(textXPath).append('\'');
        sb.append(", text='").append(text).append('\'');
        sb.append(", blobXPath='").append(blobXPath).append('\'');
        sb.append(", blob=").append(blob);
        sb.append(", properties=").append(properties);
        sb.append('}');
        return sb.toString();
    }
}
