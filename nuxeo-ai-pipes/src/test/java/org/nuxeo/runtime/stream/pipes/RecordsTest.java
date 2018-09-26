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
package org.nuxeo.runtime.stream.pipes;


import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.runtime.stream.pipes.events.EventPipesTest.TEST_MIME_TYPE;
import static org.nuxeo.runtime.stream.pipes.events.EventPipesTest.getTestEvent;
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.MAPPER;
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.fromRecord;
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.toJsonString;
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.toRecord;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.stream.pipes.functions.PropertiesToStream;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;
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

        BlobTextStream andBack = fromRecord(recs.get(0), BlobTextStream.class);
        assertNotNull(andBack);
        log.debug("Result is " + andBack);
        assertEquals("File", andBack.getPrimaryType());
        assertEquals("dc:creator", andBack.getXPaths().toArray()[0]);
        assertEquals("file:content", andBack.getXPaths().toArray()[1]);
        assertEquals(7, andBack.getBlob().getLength());
        assertEquals(TEST_MIME_TYPE, andBack.getBlob().getMimeType());
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
            assertTrue(handled.getCause().getClass().getSimpleName().equals("IOException"));
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
