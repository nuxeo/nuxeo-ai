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
package org.nuxeo.ai.enrichment;

import static com.tngtech.jgiven.impl.util.AssertionUtil.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.nuxeo.runtime.stream.pipes.events.RecordUtil.fromRecord;
import static org.nuxeo.runtime.stream.pipes.events.RecordUtil.toRecord;

import java.io.IOException;
import java.time.Instant;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

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

@RunWith(FeaturesRunner.class)
public class TestEnrichmentMetaData {

    @Test
    public void testBuilder() {
        EnrichmentMetadata metadata = new EnrichmentMetadata.Builder("me", "m1", "doc1").build();
        assertNotNull(metadata);
    }

    @Test
    public void testJson() throws IOException {
        EnrichmentMetadata metadata =
                new EnrichmentMetadata.Builder("me", "m1", "doc1")
                        .withBlobDigest("blobxx")
                        .withTargetDocumentProperty("tbloby").build();
        assertNotNull(metadata);
        Record record = toRecord("k", metadata);
        EnrichmentMetadata metadataBackAgain = fromRecord(record, EnrichmentMetadata.class);
        assertEquals(metadata, metadataBackAgain);

    }

    public static class InstantSerializer extends JsonSerializer<Instant> {

        @Override
        public void serialize(Instant instant, JsonGenerator jg, SerializerProvider serializers) throws IOException, JsonProcessingException {
            jg.writeObject(instant.toString());
        }
    }

    public static class InstantDeserializer extends JsonDeserializer<Instant> {

        @Override
        public Instant deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            String val = ctxt.readValue(jp, String.class);
            return Instant.parse(val);

        }
    }
}
