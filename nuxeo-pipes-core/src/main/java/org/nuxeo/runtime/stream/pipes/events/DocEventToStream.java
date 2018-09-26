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

import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.toDoc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;

/**
 * Take a document event and turn it into a stream BlobTextStream.
 * <p>
 * By default it looks for a blob "file:content" property
 */
public class DocEventToStream implements Function<Event, Collection<BlobTextStream>> {

    public static final String BLOB_PROPERTIES = "blobProperties";

    public static final String TEXT_PROPERTIES = "textProperties";

    public static final String CUSTOM_PROPERTIES = "customProperties";

    protected static final List<String> DEFAULT_BLOB_PROPERTIES = Collections.singletonList("file:content");

    private static final Log log = LogFactory.getLog(DocEventToStream.class);

    protected final List<String> blobProperties;

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
    public DocEventToStream(List<String> blobProperties,
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
    public Collection<BlobTextStream> apply(Event event) {
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
     * Serialize the document properties as a Collection of BlobTextStream
     */
    public Collection<BlobTextStream> docSerialize(DocumentModel doc) {
        List<BlobTextStream> items = new ArrayList<>();
        blobProperties.forEach(propName -> {
            try {
                Property property = doc.getProperty(propName);
                Blob blob = (Blob) property.getValue();
                if (blob != null && blob instanceof ManagedBlob) {
                    BlobTextStream blobTextStream = getBlobTextStream(doc);
                    blobTextStream.addXPath(propName);
                    blobTextStream.setBlob((ManagedBlob) blob);
                    items.add(blobTextStream);
                }
            } catch (PropertyNotFoundException e) {
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Unable to find blob property %s so I am ignoring it.", propName));
                }
            }
        });

        textProperties.forEach(propName -> {
            String text = getPropertyValue(doc, propName);
            if (text != null) {
                BlobTextStream blobTextStream = getBlobTextStream(doc);
                blobTextStream.addXPath(propName);
                blobTextStream.setText(text);
                items.add(blobTextStream);
            }
        });

        if (items.isEmpty() && !customProperties.isEmpty()) {
            items.add(getBlobTextStream(doc));
        }

        return items;
    }

    /**
     * Create a BlobTextStream based on the specified document
     */
    protected BlobTextStream getBlobTextStream(DocumentModel doc) {
        BlobTextStream blobTextStream =
                new BlobTextStream(doc.getId(), doc.getRepositoryName(), doc.getParentRef().toString(), doc
                        .getType(), doc.getFacets());
        Map<String, String> properties = blobTextStream.getProperties();

        customProperties.forEach(propName -> {
            String propVal = getPropertyValue(doc, propName);
            if (propVal != null) {
                properties.put(propName, propVal);
                blobTextStream.addXPath(propName);
            }
        });

        return blobTextStream;
    }

    /**
     * Get a property value as a String and handle errors
     */
    protected String getPropertyValue(DocumentModel doc, String propertyName) {
        try {
            Property property = doc.getProperty(propertyName);
            Serializable propVal = property.getValue();
            if (propVal != null) {
                return propVal.toString();
            }
        } catch (PropertyNotFoundException e) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Unable to find property %s so I am ignoring it.", propertyName));
            }
        }
        return null;
    }

}
