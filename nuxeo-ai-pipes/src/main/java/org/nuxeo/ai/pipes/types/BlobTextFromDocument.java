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
 *     Pedro Cardoso
 */
package org.nuxeo.ai.pipes.types;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.nuxeo.ai.sdk.objects.PropertyType;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A POJO representation used to transfer data in a stream. The main subject of this class is usually either a blob or a
 * piece of text taken from a Nuxeo Document.
 */
public class BlobTextFromDocument implements Partitionable, Serializable {

    private static final long serialVersionUID = 201920081233428L;

    private final Map<String, String> properties = new HashMap<>();

    private final Map<String, String> blobTypes = new HashMap<>();

    private final Map<String, ManagedBlob> blobs = new HashMap<>();

    private String id;

    private String repositoryName;

    private String parentId;

    private String primaryType;

    private Set<String> facets;

    public BlobTextFromDocument() {
    }

    public BlobTextFromDocument(String id, String repositoryName, String parentId, String primaryType,
            Set<String> facets) {
        this.id = id;
        this.repositoryName = repositoryName;
        this.parentId = parentId;
        this.primaryType = primaryType;
        this.facets = facets;
    }

    public BlobTextFromDocument(DocumentModel doc) {
        this.id = doc.getId();
        this.repositoryName = doc.getRepositoryName();
        this.parentId = String.valueOf(doc.getParentRef());
        this.primaryType = doc.getType();
        this.facets = doc.getFacets();
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

    public Map<String, ManagedBlob> getBlobs() {
        return blobs;
    }

    public Map<String, String> getBlobTypes() {
        return blobTypes;
    }

    public Map<PropertyType, ManagedBlob> computePropertyBlobs() {
        return blobs.entrySet()
                    .stream()
                    .collect(Collectors.toMap(b -> new PropertyType(b.getKey(), blobTypes.get(b.getKey())),
                            Map.Entry::getValue));
    }

    public void addBlob(String name, String type, ManagedBlob blob) {
        blobs.put(name, blob);
        blobTypes.put(name, type);
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void addProperty(String name, String propVal) {
        properties.put(name, propVal);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BlobTextFromDocument that = (BlobTextFromDocument) o;
        return Objects.equals(id, that.id) && Objects.equals(repositoryName, that.repositoryName) && Objects.equals(
                parentId, that.parentId) && Objects.equals(primaryType, that.primaryType) && Objects.equals(facets,
                that.facets) && Objects.equals(blobs, that.blobs) && Objects.equals(blobTypes, that.blobTypes)
                && Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, repositoryName, parentId, primaryType, facets, blobs, blobTypes, properties);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("id", id)
                                        .append("repositoryName", repositoryName)
                                        .append("parentId", parentId)
                                        .append("primaryType", primaryType)
                                        .append("facets", facets)
                                        .append("blobs", blobs)
                                        .append("blobTypes", blobTypes)
                                        .append("properties", properties)
                                        .toString();
    }

    @Override
    @JsonIgnore
    public String getKey() {
        return getId();
    }

}
