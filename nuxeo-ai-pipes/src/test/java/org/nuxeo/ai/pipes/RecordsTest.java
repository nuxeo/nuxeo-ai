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
package org.nuxeo.ai.pipes;


import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertNotNull;
import static org.nuxeo.ai.pipes.events.EventPipesTest.TEST_MIME_TYPE;
import static org.nuxeo.ai.pipes.events.EventPipesTest.getTestEvent;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;
import static org.nuxeo.ai.pipes.services.JacksonUtil.fromRecord;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toRecord;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.sdk.objects.PropertyType;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.ai.pipes.functions.PropertiesToStream;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({PlatformFeature.class})
public class RecordsTest {

    private static final Log log = LogFactory.getLog(RecordsTest.class);

    @Inject
    CoreSession session;

    @Test
    public void testEventsToRecord() throws Exception {
        PropertiesToStream func = new PropertiesToStream();
        Map<String, String> params = new HashMap<>();
        params.put("blobProperties", "file:content");
        params.put("blobPropertiesType", "txt");
        func.init(params);
        List<Record> recs = (List<Record>) func.apply(getTestEvent(session));
        assertEquals(1, recs.size());

        params.put("textProperties", "dc:creator");
        func.init(params);
        recs = (List<Record>) func.apply(getTestEvent(session));
        assertEquals(2, recs.size());

        params.remove("textProperties");
        params.put("customProperties", "dc:creator");
        func.init(params);
        recs = (List<Record>) func.apply(getTestEvent(session));
        assertEquals(1, recs.size());

        BlobTextFromDocument andBack = fromRecord(recs.get(0), BlobTextFromDocument.class);
        assertNotNull(andBack);
        log.debug("Result is " + andBack);
        assertEquals("File", andBack.getPrimaryType());
        assertEquals("Administrator", andBack.getProperties().get("dc:creator"));
        ManagedBlob blob =  andBack.computePropertyBlobs().get(new PropertyType("file:content", "txt"));
        assertNotNull(blob);
        assertEquals(7, blob.getLength());
        assertEquals(TEST_MIME_TYPE, blob.getMimeType());
    }

    @Test(expected = NuxeoException.class)
    public void testToRecord() throws Exception {
        toRecord("akey", getTestEvent(session));
    }

    @Test(expected = NuxeoException.class)
    public void testFromRecord() throws Exception {
        String value = "Some test";
        fromRecord(Record.of("33", value.getBytes("UTF-8")), DocumentModel.class);
    }

    @Test
    public void testJacksonUtil() throws IOException {
        String raw = toJsonString(jg -> jg.writeStringField("myField", "cake"));

        assertEquals("{\"myField\":\"cake\"}", raw);

        raw = toJsonString(null);
        assertEquals("{}", raw);

        try {
            toJsonString(jg -> {
                throw new IOException("Need to handle this");
            });
            fail();
        } catch (NuxeoException handled) {
            assertEquals("IOException", handled.getCause().getClass().getSimpleName());
        }
        Instant rightNow = Instant.now();
        String instant = toJsonString(jg -> jg.writeObjectField("now", rightNow));
        JsonNode nowNode = MAPPER.readTree(instant).get("now");
        assertEquals("ISO_INSTANT format must be used.", rightNow, Instant.parse(nowNode.asText()));

        Writer writer = new StringWriter();
        MAPPER.writeValue(writer, rightNow);
        Instant nowAgain = MAPPER.readValue(writer.toString(), Instant.class);
        assertEquals(rightNow, nowAgain);
    }
}
