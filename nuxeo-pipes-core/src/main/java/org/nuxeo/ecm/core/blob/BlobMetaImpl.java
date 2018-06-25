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

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.impl.blob.AbstractBlob;
import org.nuxeo.runtime.api.Framework;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Simple POJO implementation of ManagedBlob. That can be serialized as Json
 */
public class BlobMetaImpl extends AbstractBlob implements ManagedBlob {

    private static final long serialVersionUID = -3811624887207152594L;
    protected final String providerId;
    protected final String key;
    protected final Long length;

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
    public InputStream getStream() throws IOException {
        BlobProvider blobProvider = Framework.getService(BlobManager.class).getBlobProvider(getProviderId());
        if (blobProvider != null) {
            BlobInfo blobInfo = new BlobInfo();
            blobInfo.key = getKey();
            Blob blob = blobProvider.readBlob(blobInfo);
            if (blob != null) {
                return blob.getStream();
            }
        }
        throw new IOException("Unable to read blob: " + this);
    }

    @Override
    public long getLength() {
        return length;
    }
}
