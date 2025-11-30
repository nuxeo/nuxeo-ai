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
package org.nuxeo.ai.pipes.services;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.lib.stream.computation.Record;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Utilities for use with Jackson
 */
public class JacksonUtil {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private static final AtomicBoolean AWS_SERIALIZERS_ADDED = new AtomicBoolean(false);

    static {
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        MAPPER.deactivateDefaultTyping(); // ensure no residual default typing
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Instant.class, new InstantDeserializer());
        module.addSerializer(Instant.class, new InstantSerializer());
        // Register ManagedBlob custom (de)serializers to avoid instantiation issues for concrete implementations
        ManagedBlobSerializer managedBlobSerializer = new ManagedBlobSerializer();
        ManagedBlobDeserializer managedBlobDeserializer = new ManagedBlobDeserializer();
        module.addSerializer(ManagedBlob.class, managedBlobSerializer);
        module.addDeserializer(ManagedBlob.class, managedBlobDeserializer);
        // Attempt to register concrete SimpleManagedBlob if present so Jackson doesn't try bean construction
        try {
            Class<?> smb = Class.forName("org.nuxeo.ecm.core.blob.SimpleManagedBlob");
            module.addSerializer((Class) smb, managedBlobSerializer);
            module.addDeserializer((Class) smb, managedBlobDeserializer);
        } catch (ClassNotFoundException ignore) {
        }
        try {
            registerAwsSerializers(module); // ignore return here, static init
        } catch (Exception e) {
            // ignore if AWS SDK absent
        }
        MAPPER.registerModule(module);
        // Removed default typing activation which caused attempts to instantiate concrete ManagedBlob implementations
        MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    private static boolean registerAwsSerializers(SimpleModule module) {
        boolean added = false;
        try {
            Class<?> sdkPojoClass;
            try {
                sdkPojoClass = Class.forName("software.amazon.awssdk.core.SdkPojo");
            } catch (ClassNotFoundException primary) {
                try {
                    sdkPojoClass = Thread.currentThread()
                                         .getContextClassLoader()
                                         .loadClass("software.amazon.awssdk.core.SdkPojo");
                } catch (Exception secondary) {
                    sdkPojoClass = null;
                }
            }
            if (sdkPojoClass == null) {
                return false;
            }
            JsonSerializer<Object> sdkPojoSerializer = buildSdkPojoSerializer();
            module.addSerializer((Class) sdkPojoClass, sdkPojoSerializer);
            added = true;
            // Attempt textract Block specifically (some SDK versions create subclasses)
            try {
                Class<?> blockClass = Class.forName("software.amazon.awssdk.services.textract.model.Block");
                module.addSerializer((Class) blockClass, sdkPojoSerializer);
            } catch (Exception ignore) {
                // ignore missing Block class
            }
            // Fallback MixIn to ensure serializer selection when dynamic proxies / different CLs
            try {
                @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = JacksonUtil.SdkPojoFallbackSerializer.class)
                abstract class SdkPojoMixin {
                }
                JacksonUtil.SdkPojoFallbackSerializer.setDelegate(sdkPojoSerializer);
                MAPPER.addMixIn(sdkPojoClass, SdkPojoMixin.class);
            } catch (Exception ignore) {
                // ignore any mixin issues
            }
        } catch (Exception ignore) {
            // swallow
        }
        return added;
    }

    // Re-added method: builds a reflective JsonSerializer for AWS SdkPojo objects
    private static JsonSerializer<Object> buildSdkPojoSerializer() {
        return new JsonSerializer<>() {
            @Override
            public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeStartObject();
                for (Method m : value.getClass().getMethods()) {
                    if (m.getParameterCount() != 0 || !Modifier.isPublic(m.getModifiers())) {
                        continue;
                    }
                    if (m.getReturnType() == Void.TYPE) {
                        continue;
                    }
                    String name = m.getName();
                    if (name.equals("getClass") || name.equals("sdkFields") || name.equals("toBuilder")
                            || name.equals("builder")) {
                        continue;
                    }
                    if (name.startsWith("get") && name.length() > 3) {
                        name = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                    } else if (name.startsWith("is") && name.length() > 2
                            && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                        name = Character.toLowerCase(name.charAt(2)) + name.substring(3);
                    } else {
                        // skip non bean-style accessors
                        continue;
                    }
                    try {
                        Object fieldVal = m.invoke(value);
                        if (fieldVal == null) {
                            continue;
                        }
                        if (fieldVal instanceof java.util.Collection
                                && ((java.util.Collection<?>) fieldVal).isEmpty()) {
                            continue;
                        }
                        if (fieldVal instanceof CharSequence cs) {
                            gen.writeStringField(name, cs.toString());
                        } else if (fieldVal instanceof Number || fieldVal instanceof Boolean) {
                            gen.writeObjectField(name, fieldVal);
                        } else {
                            gen.writeFieldName(name);
                            gen.writeObject(fieldVal);
                        }
                    } catch (Exception ignore) {
                        // ignore individual property issues
                    }
                }
                gen.writeEndObject();
            }
        };
    }

    private static void ensureAwsSerializers() {
        if (AWS_SERIALIZERS_ADDED.get()) {
            return;
        }
        synchronized (AWS_SERIALIZERS_ADDED) {
            if (AWS_SERIALIZERS_ADDED.get()) {
                return;
            }
            // Register lazily on mapper (new module) to cover runtime-added AWS classes
            SimpleModule awsModule = new SimpleModule();
            boolean added = registerAwsSerializers(awsModule);
            if (added) {
                MAPPER.registerModule(awsModule);
            }
            MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            AWS_SERIALIZERS_ADDED.set(true);
        }
    }

    public static String toJsonString(JsonGeneratorConsumer withConsumer) {
        ensureAwsSerializers();
        // Defensive: ensure the feature is disabled each invocation (other modules may have re-enabled it)
        if (MAPPER.getSerializationConfig().isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS)) {
            MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        }
        StringWriter writer = new StringWriter();
        try (JsonGenerator jg = MAPPER.getFactory().createGenerator(writer)) {
            jg.writeStartObject();
            if (withConsumer != null) {
                withConsumer.accept(jg);
            }
            jg.writeEndObject();
        } catch (IOException e) {
            throw new NuxeoException("Unable to turn data into a json String", e);
        }
        return writer.toString();
    }

    /**
     * Gets the DocumentModel from an Event. Returns null if that's not possible
     */
    public static DocumentModel toDoc(Event event) {
        DocumentEventContext docCtx = (DocumentEventContext) event.getContext();
        if (docCtx == null) {
            return null;
        }
        DocumentModel doc = docCtx.getSourceDocument();
        if (doc == null) {
            return null;
        }
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
            return MAPPER.readValue(record.getData(), valueType);
        } catch (IOException e) {
            try {
                String raw = new String(record.getData(), StandardCharsets.UTF_8);
                System.out.println("JacksonUtil.fromRecord DEBUG raw json for key=" + record.getKey() + " => " + raw);
            } catch (Exception ignored) {
            }
            throw new NuxeoException("Unable to read record data for : " + record.getKey(), e);
        }
    }

    /**
     * A Consumer of JsonGenerator that throws an IOException
     */
    @FunctionalInterface
    public interface JsonGeneratorConsumer {
        void accept(JsonGenerator jg) throws IOException;
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
     * Serializes a ManagedBlob
     */
    public static class ManagedBlobSerializer extends JsonSerializer<ManagedBlob> {
        @Override
        public void serialize(ManagedBlob blob, JsonGenerator jg, SerializerProvider serializers) throws IOException {
            jg.writeStartObject();
            jg.writeStringField("mimeType", blob.getMimeType());
            jg.writeStringField("encoding", blob.getEncoding());
            jg.writeStringField("digest", blob.getDigest());
            jg.writeStringField("providerId", blob.getProviderId());
            jg.writeStringField("key", blob.getKey());
            jg.writeNumberField("length", blob.getLength());
            jg.writeEndObject();
        }
    }

    // Custom deserializer for ManagedBlob creating a lightweight dynamic proxy exposing metadata
    public static class ManagedBlobDeserializer extends JsonDeserializer<ManagedBlob> {
        @Override
        public ManagedBlob deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            final String mimeType = text(node, "mimeType");
            final String encoding = text(node, "encoding");
            final String digest = text(node, "digest");
            final String providerId = text(node, "providerId");
            final String key = text(node, "key");
            final long length = longVal(node, "length");
            InvocationHandler handler = (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getMimeType":
                        return mimeType;
                    case "getEncoding":
                        return encoding;
                    case "getDigest":
                        return digest;
                    case "getProviderId":
                        return providerId;
                    case "getKey":
                        return key;
                    case "getLength":
                        return length;
                    case "toString":
                        return "ManagedBlob{" + key + "," + mimeType + "," + length + "}";
                    case "equals":
                        if (args != null && args.length == 1 && args[0] != null
                                && Proxy.isProxyClass(args[0].getClass())) {
                            // Compare metadata of other proxy
                            Object other = args[0];
                            try {
                                String otherKey = (String) other.getClass().getMethod("getKey").invoke(other);
                                String otherDigest = (String) other.getClass().getMethod("getDigest").invoke(other);
                                Long otherLength = (Long) other.getClass().getMethod("getLength").invoke(other);
                                return Objects.equals(key, otherKey) && Objects.equals(digest, otherDigest)
                                        && Objects.equals(length, otherLength);
                            } catch (Exception ignore) {
                            }
                        }
                        return proxy == args[0];
                    case "hashCode":
                        return Objects.hash(key, digest, length);
                    default:
                        // Unsupported operations return null
                        return null;
                }
            };
            return (ManagedBlob) Proxy.newProxyInstance(ManagedBlob.class.getClassLoader(),
                    new Class[] { ManagedBlob.class }, handler);
        }

        private String text(JsonNode node, String field) {
            JsonNode n = node.get(field);
            return n == null || n.isNull() ? null : n.asText();
        }

        private long longVal(JsonNode node, String field) {
            JsonNode n = node.get(field);
            return n == null || n.isNull() ? 0L : n.asLong();
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

    // Fallback serializer used by MixIn to delegate to runtime-created sdkPojoSerializer
    public static class SdkPojoFallbackSerializer extends JsonSerializer<Object> {
        private static JsonSerializer<Object> DELEGATE;

        public static void setDelegate(JsonSerializer<Object> delegate) {
            DELEGATE = delegate;
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (DELEGATE != null) {
                DELEGATE.serialize(value, gen, serializers);
            } else {
                gen.writeStartObject();
                gen.writeEndObject();
            }
        }
    }
}
