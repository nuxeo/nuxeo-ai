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
package org.nuxeo.ecm.core.blob;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Simple POJO implementation of BlobMeta.  If ManagedBlob implements BlobMeta then this class isn't needed.
 * BlobInfo requires a provider id.
 */
public class BlobMetaImpl extends BlobInfo implements BlobMeta {

    final String providerId;

    @JsonCreator
    public BlobMetaImpl(@JsonProperty("providerId") String providerId,
                        @JsonProperty("mimeType") String mimeType,
                        @JsonProperty("key") String key,
                        @JsonProperty("digest") String digest,
                        @JsonProperty("encoding") String encoding,
                        @JsonProperty("length") long length) {
        this.providerId = providerId;
        this.mimeType = mimeType;
        this.key = key;
        this.digest = digest;
        this.encoding = encoding;
        this.length = length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BlobMetaImpl blobInfo = (BlobMetaImpl) o;
        return Objects.equals(key, blobInfo.key)
                && Objects.equals(mimeType, blobInfo.mimeType)
                && Objects.equals(encoding, blobInfo.encoding)
                && Objects.equals(filename, blobInfo.filename)
                && Objects.equals(length, blobInfo.length)
                && Objects.equals(providerId, blobInfo.providerId)
                && Objects.equals(digest, blobInfo.digest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, mimeType, encoding, filename, length, digest, providerId);
    }

    @Override
    public String getProviderId() {
        return providerId;
    }

    @Override
    public String getMimeType() {
        return mimeType;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getEncoding() {
        return encoding;
    }

    @Override
    public String getDigest() {
        return digest;
    }

    @Override
    public long getLength() {
        return length;
    }
}
