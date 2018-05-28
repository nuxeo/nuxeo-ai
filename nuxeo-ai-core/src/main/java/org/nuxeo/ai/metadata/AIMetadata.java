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

import java.time.Instant;
import java.util.Objects;

import org.apache.commons.lang.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A normalized view of metadata returned by an intelligent service
 */
public abstract class AIMetadata {

    public final Instant created;
    public final String creator;
    public final String serviceName;
    public final String kind;
    public final String repositoryName;
    public final String targetDocumentRef; //Document reference
    public final String rawKey;

    public AIMetadata(String serviceName, String kind, String repositoryName, String targetDocumentRef,
                      String creator, Instant created, String rawKey) {
        this.kind = kind;
        this.repositoryName = repositoryName;
        this.targetDocumentRef = targetDocumentRef;
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

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getTargetDocumentRef() {
        return targetDocumentRef;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AIMetadata that = (AIMetadata) o;
        return Objects.equals(created, that.created) &&
                Objects.equals(creator, that.creator) &&
                Objects.equals(serviceName, that.serviceName) &&
                Objects.equals(kind, that.kind) &&
                Objects.equals(repositoryName, that.repositoryName) &&
                Objects.equals(targetDocumentRef, that.targetDocumentRef) &&
                Objects.equals(rawKey, that.rawKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(created, creator, serviceName, kind, repositoryName, targetDocumentRef, rawKey);
    }
}
