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
package org.nuxeo.runtime.stream.pipes.functions;

import java.io.IOException;
import java.io.Serializable;
import java.util.Calendar;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.io.avro.AvroConstants;
import org.nuxeo.ecm.core.schema.types.QName;

/**
 * Utilities to work with document properties
 *
 */
public class PropertyUtils {

    private static final Log log = LogFactory.getLog(PropertyUtils.class);

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
                throw new UnsupportedOperationException("Multi-value properties are currently not supported.");
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
                        return String.join(",", doc.getFacets());
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
}
