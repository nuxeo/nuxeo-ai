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

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * A builder for AIMetadata
 */
public abstract class AbstractMetaDataBuilder {

    //mandatory
    public final Instant created;

    public final String serviceName;

    public final String kind;

    //Context
    protected final String repositoryName;

    protected final String documentRef;

    protected AIMetadata.Context context;

    protected Set<String> documentProperties;

    protected Map<String, String> properties;

    protected String blobDigest;

    //optional
    protected String rawKey;

    protected String creator;

    public AbstractMetaDataBuilder(Instant created, String kind, String serviceName,
                                   String repositoryName, String documentRef,
                                   String blobDigest,
                                   Set<String> documentProperties, Map<String, String> properties) {
        this.created = created;
        this.kind = kind;
        this.serviceName = serviceName;
        this.repositoryName = repositoryName;
        this.documentRef = documentRef;
        this.blobDigest = blobDigest;
        this.documentProperties = documentProperties;
        this.properties = properties;
    }

    public AbstractMetaDataBuilder(Instant created, String kind, String serviceName, AIMetadata.Context context) {
        this.created = created;
        this.kind = kind;
        this.serviceName = serviceName;
        if (context == null) {
            throw new IllegalArgumentException("You must specify a valid context.");
        }
        this.context = context;
        this.repositoryName = context.repositoryName;
        this.documentRef = context.documentRef;
        this.blobDigest = context.blobDigest;
        this.documentProperties = context.documentProperties;
        this.properties = context.properties;
    }

    public AIMetadata.Context getContext() {
        return context;
    }

    public String getRawKey() {
        return rawKey;
    }

    public String getCreator() {
        return creator;
    }

    public AbstractMetaDataBuilder withDocumentProperties(Set<String> targetDocumentProperty) {
        this.documentProperties = targetDocumentProperty;
        return this;
    }

    public AbstractMetaDataBuilder withCustomProperties(Map<String, String> properties) {
        this.properties = properties;
        return this;
    }

    public AbstractMetaDataBuilder withCreator(String creator) {
        this.creator = creator;
        return this;
    }

    public AbstractMetaDataBuilder withRawKey(String rawBlobKey) {
        this.rawKey = rawBlobKey;
        return this;
    }

    public AbstractMetaDataBuilder withBlobDigest(String blobDigest) {
        this.blobDigest = blobDigest;
        return this;
    }

    public <T extends AIMetadata> T build() {
        if (StringUtils.isBlank(serviceName)
                || StringUtils.isBlank(kind)
                || StringUtils.isBlank(documentRef)
                || StringUtils.isBlank(repositoryName)
                || created == null) {
            throw new IllegalArgumentException("Invalid metadata has been given. " + this.toString());
        }

        if (documentProperties == null) {
            documentProperties = emptySet();
        }
        if (properties == null) {
            properties = emptyMap();
        }
        if (context == null) {
            context = new AIMetadata.Context(repositoryName, documentRef, blobDigest, documentProperties, properties);
        }
        return build(this);
    }

    protected abstract <T extends AIMetadata> T build(AbstractMetaDataBuilder abstractMetaDataBuilder);
}
