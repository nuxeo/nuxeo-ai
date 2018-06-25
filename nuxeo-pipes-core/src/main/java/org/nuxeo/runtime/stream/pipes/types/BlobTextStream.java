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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.nuxeo.ecm.core.blob.ManagedBlob;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A POJO representation of BlobTextStream.avsc used as a record data in a stream.
 * <p>
 * The main subject of this class is usually either a blob or a piece of text (not a Nuxeo Document).
 * The List<String> xPaths contains the names of the original document properties used.
 * <p>
 * Additional properties can be held in the "properties" map.
 */
public class BlobTextStream implements Partitionable {

    private final Set<String> xPaths = new LinkedHashSet<>();
    private final Map<String, String> properties = new HashMap<>();
    private String id;
    private String repositoryName;
    private String parentId;
    private String primaryType;
    private Set<String> facets;
    private String text;
    private ManagedBlob blob;

    public BlobTextStream() {
    }

    public BlobTextStream(String id, String repositoryName, String parentId, String primaryType, Set<String> facets) {
        this.id = id;
        this.repositoryName = repositoryName;
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

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
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

    public void addXPath(String propName) {
        xPaths.add(propName);
    }

    public Set<String> getXPaths() {
        return xPaths;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public ManagedBlob getBlob() {
        return blob;
    }

    public void setBlob(ManagedBlob blob) {
        this.blob = blob;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BlobTextStream that = (BlobTextStream) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(repositoryName, that.repositoryName) &&
                Objects.equals(parentId, that.parentId) &&
                Objects.equals(primaryType, that.primaryType) &&
                Objects.equals(facets, that.facets) &&
                Objects.equals(text, that.text) &&
                Objects.equals(blob, that.blob) &&
                Objects.equals(xPaths, that.xPaths) &&
                Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, repositoryName, parentId, primaryType, facets, text, blob, xPaths, properties);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("repositoryName", repositoryName)
                .append("parentId", parentId)
                .append("primaryType", primaryType)
                .append("facets", facets)
                .append("text", text)
                .append("blob", blob)
                .append("xPaths", xPaths)
                .append("properties", properties)
                .toString();
    }

    @Override
    @JsonIgnore
    public String getKey() {
        return getId();
    }

}
