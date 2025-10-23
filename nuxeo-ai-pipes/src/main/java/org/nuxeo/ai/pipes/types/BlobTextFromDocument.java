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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.nuxeo.ai.pipes.services.JacksonUtil;

/**
 * A POJO representation used to transfer data in a stream. The main subject of this class is usually either a blob or a
 * piece of text taken from a Nuxeo Document.
 */
public class BlobTextFromDocument implements Partitionable, Serializable {

    private static final long serialVersionUID = 201920081233428L;

    private final Map<String, String> properties = new HashMap<>();

    private final Map<String, String> blobTypes = new HashMap<>();

    @JsonDeserialize(contentUsing = JacksonUtil.ManagedBlobDeserializer.class)
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

    public String getProperty(String name) {
        return properties.get(name);
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
        if (!Objects.equals(id, that.id) || !Objects.equals(repositoryName, that.repositoryName) || !Objects.equals(parentId, that.parentId)
                || !Objects.equals(primaryType, that.primaryType) || !Objects.equals(facets, that.facets)
                || !Objects.equals(blobTypes, that.blobTypes) || !Objects.equals(properties, that.properties)) {
            return false;
        }
        // Compare blobs by metadata rather than relying on ManagedBlob.equals (proxy vs concrete)
        if (blobs.size() != that.blobs.size()) {
            return false;
        }
        for (Map.Entry<String, ManagedBlob> e : blobs.entrySet()) {
            ManagedBlob otherBlob = that.blobs.get(e.getKey());
            ManagedBlob thisBlob = e.getValue();
            if (!blobMetaEquals(thisBlob, otherBlob)) {
                return false;
            }
        }
        return true;
    }

    private boolean blobMetaEquals(ManagedBlob a, ManagedBlob b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        try {
            return Objects.equals(a.getKey(), b.getKey()) && Objects.equals(a.getDigest(), b.getDigest())
                    && Objects.equals(a.getMimeType(), b.getMimeType()) && Objects.equals(a.getEncoding(), b.getEncoding())
                    && Objects.equals(a.getProviderId(), b.getProviderId()) && a.getLength() == b.getLength();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int blobsHash = 0;
        for (Map.Entry<String, ManagedBlob> e : blobs.entrySet()) {
            ManagedBlob mb = e.getValue();
            if (mb != null) {
                blobsHash += Objects.hash(e.getKey(), safe(mb.getKey()), safe(mb.getDigest()), safe(mb.getMimeType()),
                        safe(mb.getEncoding()), safe(mb.getProviderId()), mb.getLength());
            } else {
                blobsHash += Objects.hash(e.getKey(), null);
            }
        }
        return Objects.hash(id, repositoryName, parentId, primaryType, facets, blobsHash, blobTypes, properties);
    }

    private Object safe(Object v) { return v; }

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
