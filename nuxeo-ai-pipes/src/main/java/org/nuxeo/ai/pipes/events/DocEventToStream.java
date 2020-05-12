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
package org.nuxeo.ai.pipes.events;

import static org.nuxeo.ai.pipes.functions.PropertyUtils.*;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toDoc;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.pipes.types.PropertyType;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.event.Event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Take a document event and turn it into a stream BlobTextFromDocument.
 * <p>
 * By default it looks for a blob "file:content" property
 */
public class DocEventToStream implements Function<Event, Collection<BlobTextFromDocument>> {

    public static final String BLOB_PROPERTIES = "blobProperties";
    
    public static final String BLOB_PROPERTIES_TYPE = "blobPropertiesType";

    public static final String TEXT_PROPERTIES = "textProperties";

    public static final String CUSTOM_PROPERTIES = "customProperties";

    protected static final List<PropertyType> DEFAULT_BLOB_PROPERTIES = Collections.singletonList(new PropertyType(FILE_CONTENT, IMAGE_TYPE));

    private static final Log log = LogFactory.getLog(DocEventToStream.class);

    protected final List<PropertyType> blobProperties;

    protected final List<String> textProperties;

    protected final List<String> customProperties;

    /**
     * Creates an instance with default values
     */
    public DocEventToStream() {
        this(DEFAULT_BLOB_PROPERTIES, null, null);
    }

    /**
     * Creates an instance with the specified values
     */
    public DocEventToStream(List<PropertyType> blobProperties,
                            List<String> textProperties,
                            List<String> customProperties) {
        this.blobProperties = blobProperties != null ? blobProperties : Collections.emptyList();
        this.textProperties = textProperties != null ? textProperties : Collections.emptyList();
        this.customProperties = customProperties != null ? customProperties : Collections.emptyList();

        if (this.blobProperties.isEmpty() && this.textProperties.isEmpty() && this.customProperties.isEmpty()) {
            throw new IllegalArgumentException("DocEventToStream requires at least one property name.");
        }
    }

    @Override
    public Collection<BlobTextFromDocument> apply(Event event) {
        DocumentModel doc = toDoc(event);
        if (doc != null) {
            try {
                return docSerialize(doc);
            } catch (PropertyException e) {
                log.error("Unable to serialize event document", e);
                throw e;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Serialize the document properties as a Collection of BlobTextFromDocument
     */
    public Collection<BlobTextFromDocument> docSerialize(DocumentModel doc) {
        List<BlobTextFromDocument> items = new ArrayList<>();
        blobProperties.forEach(property -> {
            Blob blob = getPropertyValue(doc, property.getName(), Blob.class);
            if (blob instanceof ManagedBlob) {
                BlobTextFromDocument blobTextFromDoc = getBlobText(doc);
                blobTextFromDoc.addBlob(property.getName(), property.getType(), (ManagedBlob) blob);
                items.add(blobTextFromDoc);
            }
        });

        textProperties.forEach(propName -> {
            String text = getPropertyValue(doc, propName, String.class);
            if (text != null) {
                BlobTextFromDocument blobTextFromDoc = getBlobText(doc);
                blobTextFromDoc.addProperty(propName, text);
                items.add(blobTextFromDoc);
            }
        });

        if (items.isEmpty() && !customProperties.isEmpty()) {
            items.add(getBlobText(doc));
        }

        return items;
    }

    /**
     * Create a BlobTextFromDocument based on the specified document
     */
    protected BlobTextFromDocument getBlobText(DocumentModel doc) {
        BlobTextFromDocument blobTextFromDoc =
                new BlobTextFromDocument(doc.getId(), doc.getRepositoryName(), doc.getParentRef().toString(), doc
                        .getType(), doc.getFacets());

        customProperties.forEach(propName -> {
            String propVal = getPropertyValue(doc, propName, String.class);
            if (propVal != null) {
                blobTextFromDoc.addProperty(propName, propVal);
            }
        });

        return blobTextFromDoc;
    }

}
