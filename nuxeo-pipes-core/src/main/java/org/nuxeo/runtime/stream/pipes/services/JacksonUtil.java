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
package org.nuxeo.runtime.stream.pipes.services;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.lib.stream.computation.Record;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Utilities for use with Jackson
 */
public class JacksonUtil {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Instant.class, new InstantDeserializer());
        module.addSerializer(Instant.class, new InstantSerializer());
        MAPPER.registerModule(module);
    }

    public static String toJsonString(JsonGeneratorConsumer withConsumer) {
        StringWriter writer = new StringWriter();
        try (JsonGenerator jg = MAPPER.getFactory().createGenerator(writer)) {
            jg.writeStartObject();
            if (withConsumer != null) {
                withConsumer.accept(jg);
            }
            jg.writeEndObject();
        } catch (IOException e) {
            throw new NuxeoException("Unabled to turn data into a json String", e);
        }
        return writer.toString();
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
            return Record.of(key, MAPPER.writeValueAsBytes(info));
        } catch (JsonProcessingException e) {
            throw new NuxeoException("Unable to serialize properties for: " + key, e);
        }
    }

    /**
     * Creates a record from a Type
     */
    public static <T> T fromRecord(Record record, Class<T> valueType) {

        try {
            return MAPPER.readValue(record.data, valueType);
        } catch (IOException e) {
            throw new NuxeoException("Unable to read record data for : " + record.key, e);
        }
    }

    /**
     * A Consumer of JsonGenerator that throws an IOException
     */
    @FunctionalInterface
    public static interface JsonGeneratorConsumer {
        void accept(JsonGenerator jg) throws IOException;
    }

    /**
     * Allows Jackson to treat a String of JSON as a raw string.
     */
    public static class JsonRawValueDeserializer extends JsonDeserializer<String> {

        @Override
        public String deserialize(JsonParser jp, DeserializationContext context)
                throws IOException {
            return jp.readValueAsTree().toString();
        }
    }

    /**
     * Serializes an instant
     */
    public static class InstantSerializer extends JsonSerializer<Instant> {

        @Override
        public void serialize(Instant instant, JsonGenerator jg, SerializerProvider serializers) throws IOException {
            jg.writeObject(instant.toString());
        }
    }

    /**
     * Deserializes an instant
     */
    public static class InstantDeserializer extends JsonDeserializer<Instant> {

        @Override
        public Instant deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String val = ctxt.readValue(jp, String.class);
            return Instant.parse(val);

        }
    }


    /**
     * Serializes an instant
     */
    public static class InstantSerializer extends JsonSerializer<Instant> {

        @Override
        public void serialize(Instant instant, JsonGenerator jg, SerializerProvider serializers) throws IOException {
            jg.writeObject(instant.toString());
        }
    }

    /**
     * Deserializes an instant
     */
    public static class InstantDeserializer extends JsonDeserializer<Instant> {

        @Override
        public Instant deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String val = ctxt.readValue(jp, String.class);
            return Instant.parse(val);

        }
    }
}
