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

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.nuxeo.runtime.stream.pipes.PipesTestConfigFeature.PIPES_TEST_CONFIG;
import static org.nuxeo.runtime.stream.pipes.events.EventPipesTest.getTestEvent;
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.fromRecord;
import static org.nuxeo.runtime.stream.pipes.streams.FunctionStreamProcessor.buildName;
import static org.nuxeo.runtime.stream.pipes.streams.FunctionStreamProcessor.getStreamsList;

import java.time.Duration;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.lib.stream.log.LogRecord;
import org.nuxeo.lib.stream.log.LogTailer;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.StreamService;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({PipesTestConfigFeature.class, PlatformFeature.class})
@Deploy({"org.nuxeo.runtime.stream", "org.nuxeo.runtime.stream.pipes.nuxeo-pipes",
        "org.nuxeo.runtime.stream.pipes.nuxeo-pipes:OSGI-INF/stream-pipes-test.xml"})
public class StreamsPipesTest {

    @Inject
    CoreSession session;

    @Inject
    EventService eventService;

    @Test
    public void testPipes() throws Exception {

        Event event = getTestEvent(session);
        LogManager manager = Framework.getService(StreamService.class).getLogManager(PIPES_TEST_CONFIG);

        //First check the event goes from text-> text.pass -> text.out streams
        try (LogTailer<Record> tailer = manager.createTailer("group", "text.out")) {
            assertEquals(null, tailer.read(Duration.ofSeconds(1)));
            eventService.fireEvent(event);
            eventService.waitForAsyncCompletion();
            LogRecord<Record> record = tailer.read(Duration.ofSeconds(5));
            assertNotNull(record.message());
        }

        DocumentModel theTestDoc;
        //Now check the MimeBlobPropertyFilter filter the blob by "text" mimetype
        try (LogTailer<Record> tailer = manager.createTailer("group", "pipe.text.out")) {
            LogRecord<Record> record = tailer.read(Duration.ofSeconds(1));
            assertNotNull(record.message());
            BlobTextStream andBack = fromRecord(record.message(), BlobTextStream.class);
            theTestDoc = session.getDocument(new IdRef(andBack.getId()));
        }

        //Modify the test document and check the dirty listeners ran
        try (LogTailer<Record> tailer = manager.createTailer("group", "pipe.dirty.out")) {
            assertEquals(null, tailer.read(Duration.ofSeconds(1)));
            theTestDoc.setPropertyValue("dc:title", "Dirty Document");
            session.saveDocument(theTestDoc);
            LogRecord<Record> record = tailer.read(Duration.ofSeconds(5));
            assertNotNull(record.message());
            BlobTextStream dirtyDoc = fromRecord(record.message(), BlobTextStream.class);
            assertEquals("Dirty Document", dirtyDoc.getText());
        }

        //Now check closing, it doesn't throw an error
        manager.close();
    }

    @Test
    public void testStreamsList() {
        try {
            getStreamsList(null, null);
            assertTrue("The call should have failed", false);
        } catch (IllegalArgumentException ignored) {
        }

        List<String> streams = getStreamsList("bob", null);
        assertEquals(1, streams.size());
        assertEquals("i1:bob", streams.get(0));

        streams = getStreamsList("bob", "hope");
        assertNotNull(streams);
        assertEquals("i1:bob", streams.get(0));
        assertEquals("o1:hope", streams.get(1));

        streams = getStreamsList("bob", "hope,nope,rope");
        assertNotNull(streams);
        assertEquals("o1:hope", streams.get(1));
        assertEquals("o2:nope", streams.get(2));
        assertEquals("o3:rope", streams.get(3));
    }

    @Test
    public void testBuildName() {
        assertEquals("Should not error even though nulls passed in",
                     "aname", buildName("aname", null, null)
        );
        assertEquals("bob>king>hope", buildName("king", "bob", "hope"));
        assertEquals("bob>king>hope,rope", buildName("king", "bob", "hope,rope"));

        assertEquals("bob>king", buildName("king", "bob", null));
        assertEquals("king>kong", buildName("king", null, "kong"));
    }
}
