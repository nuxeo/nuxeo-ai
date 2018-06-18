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
package org.nuxeo.ai.metadata;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A normalized view of metadata returned by an intelligent service
 */
public abstract class AIMetadata implements Serializable {

    private static final long serialVersionUID = 4590486107702875095L;
    public final Instant created;
    public final String creator;
    public final String serviceName;
    public final Context context;
    public final String kind;
    public final String rawKey;

    public AIMetadata(String serviceName, String kind, Context context,
                      String creator, Instant created, String rawKey) {
        this.kind = kind;
        this.context = context;
        this.created = created;
        this.creator = creator;
        this.rawKey = rawKey;
        this.serviceName = serviceName;
    }

    public String getKind() {
        return kind;
    }

    public Instant getCreated() {
        return created;
    }

    public String getCreator() {
        return creator;
    }

    public String getRawKey() {
        return rawKey;
    }

    public String getServiceName() {
        return serviceName;
    }

    @JsonIgnore
    public boolean isHuman() {
        return StringUtils.isNotEmpty(creator);
    }

    public Context getContext() {
        return context;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        AIMetadata that = (AIMetadata) o;
        return Objects.equals(created, that.created) &&
                Objects.equals(creator, that.creator) &&
                Objects.equals(serviceName, that.serviceName) &&
                Objects.equals(kind, that.kind) &&
                Objects.equals(context, that.context) &&
                Objects.equals(rawKey, that.rawKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(created, creator, serviceName, kind, context, rawKey);
    }

    /**
     * The context about which the metadata was created
     */
    public static class Context implements Serializable {

        private static final long serialVersionUID = 3212595777234047338L;
        public final String repositoryName;
        public final String documentRef; //Document reference
        public final String blobDigest;
        public final Set<String> documentProperties;
        public final Map<String, String> properties;

        @JsonCreator
        public Context(@JsonProperty("repositoryName") String repositoryName,
                       @JsonProperty("documentRef") String documentRef,
                       @JsonProperty("blobDigest") String blobDigest,
                       @JsonProperty("documentProperties") Set<String> documentProperties,
                       @JsonProperty("properties") Map<String, String> properties) {
            this.repositoryName = repositoryName;
            this.documentRef = documentRef;
            this.blobDigest = blobDigest;
            this.documentProperties = documentProperties != null ? unmodifiableSet(documentProperties) : emptySet();
            this.properties = properties != null ? unmodifiableMap(properties) : emptyMap();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) { return true; }
            if (o == null || getClass() != o.getClass()) { return false; }
            Context context = (Context) o;
            return Objects.equals(repositoryName, context.repositoryName) &&
                    Objects.equals(documentRef, context.documentRef) &&
                    Objects.equals(blobDigest, context.blobDigest) &&
                    Objects.equals(documentProperties, context.documentProperties) &&
                    Objects.equals(properties, context.properties);
        }

        @Override
        public int hashCode() {
            return Objects.hash(repositoryName, documentRef, blobDigest, documentProperties, properties);
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .append("repositoryName", repositoryName)
                    .append("documentRef", documentRef)
                    .append("blobDigest", blobDigest)
                    .append("documentProperties", documentProperties)
                    .append("properties", properties)
                    .toString();
        }
    }
}
