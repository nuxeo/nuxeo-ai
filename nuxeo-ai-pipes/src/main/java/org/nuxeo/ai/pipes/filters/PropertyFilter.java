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
package org.nuxeo.ai.pipes.filters;

import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ai.pipes.streams.Initializable;

/**
 * Filters document properties
 */
public abstract class PropertyFilter implements Filter.DocumentFilter, Initializable {

    protected List<String> properties;

    @Override
    public void init(Map<String, String> options) {
        properties = propsList(options.get("properties"));
    }

    /**
     * Returns true if ANY of the properties match the condition
     */
    @Override
    public boolean test(DocumentModel documentModel) {
        return documentModel != null && properties.stream().anyMatch(prop -> testProperty(documentModel, prop));
    }

    /**
     * Returns true if the property is found an matches the condition
     */
    public boolean testProperty(DocumentModel documentModel, String xPath) {
        try {
            Property property = documentModel.getProperty(xPath);
            return testProperty(property);
        } catch (PropertyNotFoundException e) {
            //Ignore exception
            return false;
        }

    }

    /**
     * Returns true if the property matches the condition
     */
    public abstract boolean testProperty(Property property);

    /**
     * A filter for a blob property
     */
    public abstract static class BlobPropertyFilter extends PropertyFilter {

        @Override
        public boolean testProperty(Property property) {
            try {
                Blob blob = (Blob) property.getValue();
                if (blob != null) {
                    return testBlob(blob);
                }
            } catch (ClassCastException e) {
                //Ignore exception
            }
            return false;
        }

        /**
         * Returns true if the blob matches the condition
         */
        public abstract boolean testBlob(Blob blob);
    }

}
