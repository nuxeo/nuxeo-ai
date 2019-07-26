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
package org.nuxeo.ai.pipes.functions;

import static org.nuxeo.ecm.core.api.AbstractSession.BINARY_TEXT_SYS_PROP;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.io.avro.AvroConstants;
import org.nuxeo.ecm.core.schema.types.QName;

/**
 * Utilities to work with document properties
 */
public class PropertyUtils {

    public static final String FILE_CONTENT = "file:content";

    public static final String FULL_TEXT = "fulltext";

    public static final String BINARY_TEXT = "binarytext";

    public static final String LIST_DELIMITER = " | ";

    public static final String LIST_DELIMITER_PATTERN = Pattern.quote(LIST_DELIMITER);

    public static final String NAME_PROP = "name";

    public static final String TYPE_PROP = "type";

    public static final String IMAGE_TYPE = "img";

    public static final String TEXT_TYPE = "txt";

    public static final String CATEGORY_TYPE = "cat";

    private static final Logger log = LogManager.getLogger(PropertyUtils.class);

    // utility class
    private PropertyUtils() {
    }

    /**
     * Gets property value and handle errors
     */
    @SuppressWarnings("unchecked")
    public static <T> T getPropertyValue(DocumentModel doc, String propertyName, Class<T> type) {
        Serializable propVal = getPropertyValue(doc, propertyName);
        if (propVal != null) {
            if (type.isAssignableFrom(propVal.getClass())) {
                return (T) propVal;
            } else if (propVal instanceof Blob && type.isAssignableFrom(String.class)) {
                return (T) base64EncodeBlob((Blob) propVal);
            } else if (propVal instanceof Calendar && type.isAssignableFrom(String.class)) {
                return (T) ((Calendar) propVal).toInstant().toString();
            } else if (propVal.getClass().isArray() &&
                    propVal.getClass().getComponentType().isAssignableFrom(String.class)) {
                return (T) serializeArray(propVal);
            } else if (type.isAssignableFrom(String.class)) {
                return (T) propVal.toString();
            } else {
                throw new UnsupportedOperationException("Converting complex properties is currently not supported.");
            }
        }
        return null;
    }

    /**
     * Base64 encode the blob bytes
     */
    public static String base64EncodeBlob(Blob blob) {
        try {
            if (blob != null) {
                return Base64.encodeBase64String(blob.getByteArray());
            }
        } catch (IOException ioe) {
            log.warn("Failed to convert a blob to a String", ioe);
        }
        return null;
    }

    /**
     * Get a property value.  Also handles 'ecm:' property types
     * Returns null if not found.
     */
    public static Serializable getPropertyValue(DocumentModel doc, String propertyName) {
        try {
            return doc.getProperty(propertyName).getValue();
        } catch (PropertyNotFoundException e) {
            QName qName = QName.valueOf(propertyName);
            if (AvroConstants.ECM.equals(qName.getPrefix())) {
                switch (qName.getLocalName()) {
                    case AvroConstants.UUID:
                        return doc.getId();
                    case AvroConstants.NAME:
                        return doc.getName();
                    case AvroConstants.TITLE:
                        return doc.getTitle();
                    case AvroConstants.PATH:
                        return doc.getPathAsString();
                    case AvroConstants.REPOSITORY_NAME:
                        return doc.getRepositoryName();
                    case AvroConstants.PRIMARY_TYPE:
                        return doc.getType();
                    case AvroConstants.MIXIN_TYPES:
                        return String.join(LIST_DELIMITER, doc.getFacets());
                    case AvroConstants.PARENT_ID:
                        DocumentRef parentRef = doc.getParentRef();
                        return parentRef != null ? parentRef.toString() : null;
                    case AvroConstants.CURRENT_LIFE_CYCLE_STATE:
                        return doc.getCurrentLifeCycleState();
                    case AvroConstants.VERSION_LABEL:
                        return doc.getVersionLabel();
                    case AvroConstants.VERSION_VERSIONABLE_ID:
                        return doc.getVersionSeriesId();
                    case AvroConstants.POS:
                        return doc.getPos();
                    case AvroConstants.IS_PROXY:
                        return doc.isProxy();
                    case AvroConstants.IS_TRASHED:
                        return doc.isTrashed();
                    case AvroConstants.IS_VERSION:
                        return doc.isVersion();
                    case AvroConstants.IS_CHECKEDIN:
                        return !doc.isCheckedOut();
                    case AvroConstants.IS_LATEST_VERSION:
                        return doc.isLatestVersion();
                    case AvroConstants.IS_LATEST_MAJOR_VERSION:
                        return doc.isLatestMajorVersion();
                    case FULL_TEXT:
                    case BINARY_TEXT:
                    case BINARY_TEXT_SYS_PROP:
                        Map<String, String> bmap = doc.getBinaryFulltext();
                        return bmap.get(BINARY_TEXT);
                }
            }
            if (log.isDebugEnabled()) {
                log.debug(String.format("Unknown property %s so I am ignoring it.", propertyName));
            }
            return null;
        }

    }

    /**
     * Returns true if the specified property name exists and is not null
     */
    public static boolean notNull(DocumentModel doc, String propertyName) {
        return getPropertyValue(doc, propertyName) != null;
    }

    /**
     * Convert a document to BlobTextFromDocument with only a sub-set of the properties.
     */
    public static BlobTextFromDocument docSerialize(DocumentModel doc, List<String> propertiesList) {
        BlobTextFromDocument blobTextFromDoc = new BlobTextFromDocument(doc);
        Map<String, String> properties = blobTextFromDoc.getProperties();

        propertiesList.forEach(propName -> {
            Serializable propVal = getPropertyValue(doc, propName);
            if (propVal instanceof ManagedBlob) {
                blobTextFromDoc.addBlob(propName, (ManagedBlob) propVal);
            } else if (propVal != null) {
                if (propVal.getClass().isArray()) {
                    properties.put(propName, serializeArray(propVal));
                } else {
                    properties.put(propName, propVal.toString());
                }
            }

        });

        if (log.isDebugEnabled() && properties.size() + blobTextFromDoc.getBlobs().size() != propertiesList.size()) {
            log.debug(String.format("Document %s one of the following properties is null. %s",
                                    doc.getId(), propertiesList));
        }

        return blobTextFromDoc;
    }

    /**
     * Turn an array into a list.
     */
    protected static String serializeArray(Serializable propVal) {
        StringBuilder valueBuilder = new StringBuilder();
        int length = Array.getLength(propVal);
        for (int i = 0; i < length; i++) {
            valueBuilder.append(Array.get(propVal, i));
            if (i != length - 1) {
                valueBuilder.append(LIST_DELIMITER);
            }
        }
        return valueBuilder.toString();
    }
}
