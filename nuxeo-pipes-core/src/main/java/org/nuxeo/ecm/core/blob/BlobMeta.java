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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Metadata about a blob, without needing an actual blob itself. Like BlobInfo but with provider.
 */
@JsonDeserialize(as=BlobMetaImpl.class)
public interface BlobMeta {

    /**
     * Gets the data length in bytes if known.
     *
     * @return the data length or -1 if not known
     */
    long getLength();

    String getMimeType();

    String getEncoding();

    /**
     * Gets the id of the {@link BlobProvider} managing this blob.
     *
     * @return the blob provider id
     */
    String getProviderId();

    /**
     * Gets the stored representation of this blob.
     *
     * @return the stored representation
     */
    String getKey();
}
