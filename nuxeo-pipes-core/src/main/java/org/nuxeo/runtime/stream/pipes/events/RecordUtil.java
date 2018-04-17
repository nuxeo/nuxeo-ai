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

import java.io.IOException;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.lib.stream.computation.Record;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RecordUtil {

    protected static ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    private RecordUtil() {
    }

    /**
     * Gets the DocumentModel from an Event.  Returns null if that's not possible
     */
    public static DocumentModel toDoc(Event event) {
        DocumentEventContext docCtx = (DocumentEventContext) event.getContext();
        if (docCtx == null) return null;
        DocumentModel doc = docCtx.getSourceDocument();
        if (doc == null) return null;
        return doc;
    }

    /**
     * Creates a record from an object
     */
    public static Record toRecord(String key, Object info) {
        try {
            return Record.of(key, mapper.writeValueAsBytes(info));
        } catch (JsonProcessingException e) {
            throw new NuxeoException("Unable to serialize properties for: " + key, e);
        }
    }

    /**
     * Creates a record from a Type
     */
    public static <T> T fromRecord(Record record, Class<T> valueType) {

        try {
            return mapper.readValue(record.data, valueType);
        } catch (IOException e) {
            throw new NuxeoException("Unable to read record data for : " + record.key, e);
        }
    }

}
