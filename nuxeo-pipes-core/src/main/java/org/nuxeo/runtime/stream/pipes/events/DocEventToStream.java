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
package org.nuxeo.runtime.stream.pipes.events;

import static org.nuxeo.runtime.stream.pipes.events.RecordUtil.toDoc;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.blob.BlobMeta;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.runtime.stream.pipes.types.DocStream;

/**
 * Take a document event and turn it into a stream DocStream.
 */
public class DocEventToStream implements Function<Event, DocStream> {

    @Override
    public DocStream apply(Event event) {
        DocumentModel doc = toDoc(event);
        if (doc != null) {
            return docSerialize(doc);
        }
        return null;
    }

    public DocStream docSerialize(DocumentModel doc) {
        Map<String, BlobMeta> blobs = withBlobs(doc);
        return withDoc(doc, blobs);
    }

    /**
     * Creates default properties with the DocumentModel
     */
    public DocStream withDoc(DocumentModel doc, Map<String, BlobMeta> blobs) {
        DocStream docStream = new DocStream(doc.getId(), doc.getParentRef().toString(), doc.getType(), doc.getFacets());
        docStream.getBlobs().putAll(blobs);
        return docStream;
    }

    /**
     * Adds blob information
     */
    protected Map<String, BlobMeta> withBlobs(DocumentModel doc) {
        Map<String, BlobMeta> blobs = new HashMap<>();
        Blob blob = (Blob) doc.getPropertyValue("file:content");
        if (blob != null && blob instanceof ManagedBlob) {
            blobs.put("default", getBlobInfo((ManagedBlob) blob));
        }
        return blobs;
    }

    protected BlobMeta getBlobInfo(ManagedBlob blob) {
        //Hopefully, in the future ManagedBlob will implement BlobMeta
        return new BlobMetaImpl(blob.getProviderId(), blob.getMimeType(), blob.getKey(),
                                blob.getEncoding(), blob.getLength()
        );
    }

}
