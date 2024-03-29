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

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.nuxeo.ai.pipes.events.EventPipesTest.getTestEvent;
import static org.nuxeo.ai.pipes.services.JacksonUtil.fromRecord;
import static org.nuxeo.ai.pipes.streams.FunctionStreamProcessor.buildName;
import static org.nuxeo.ai.pipes.streams.FunctionStreamProcessor.getStreamsList;
import static org.nuxeo.ecm.core.api.AbstractSession.BINARY_TEXT_SYS_PROP;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.lib.stream.log.LogRecord;
import org.nuxeo.lib.stream.log.LogTailer;
import org.nuxeo.lib.stream.log.Name;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.IgnoreKafka;
import org.nuxeo.runtime.stream.StreamService;
import org.nuxeo.runtime.test.runner.ConditionalIgnoreRule;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

@RunWith(FeaturesRunner.class)
@Features({ PipesTestConfigFeature.class, PlatformFeature.class })
@Deploy({ "org.nuxeo.runtime.stream", "org.nuxeo.ai.nuxeo-ai-pipes",
        "org.nuxeo.ai.ai-pipes-test:OSGI-INF/stream-pipes-test.xml" })
public class StreamsPipesTest {

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    CoreSession session;

    @Inject
    EventService eventService;

    protected static final Name TEST_GROUP = Name.ofUrn("test/group");

    protected static final Name TEXT_OUT = Name.ofUrn("test/text-out");

    protected static final Name PIPE_TEXT_OUT = Name.ofUrn("test/pipe-text-out");

    protected static final Name PIPE_DIRTY_OUT = Name.ofUrn("test/pipe-dirty-out");

    protected static final Name DEFAULT_BINARY_TEXT = Name.ofUrn("test/default-binary-text");

    protected static final Name CUSTOM_BINARY_TEXT = Name.ofUrn("test/custom-binary-text");

    @Test
    public void testPipes() throws Exception {
        Event event = getTestEvent(session);
        LogManager manager = Framework.getService(StreamService.class).getLogManager();
        // First check the event goes from text-> text-pass -> text-out streams
        try (LogTailer<Record> tailer = manager.createTailer(TEST_GROUP, TEXT_OUT)) {
            assertNull(tailer.read(Duration.ofSeconds(1)));
            eventService.fireEvent(event);
            eventService.waitForAsyncCompletion();
            LogRecord<Record> record = tailer.read(Duration.ofSeconds(5));
            assertNotNull(record);
        } catch (IllegalArgumentException e) {
            // here
            System.out.println(manager);
            System.out.println(event);
        }

        DocumentModel theTestDoc;
        //Now check the MimeBlobPropertyFilter filter the blob by "text" mimetype
        try (LogTailer<Record> tailer = manager.createTailer(TEST_GROUP, PIPE_TEXT_OUT)) {
            LogRecord<Record> record = tailer.read(Duration.ofSeconds(1));
            assertNotNull(record);
            BlobTextFromDocument andBack = fromRecord(record.message(), BlobTextFromDocument.class);
            theTestDoc = session.getDocument(new IdRef(andBack.getId()));
        }

        //Modify the test document and check the dirty listeners ran
        try (LogTailer<Record> tailer = manager.createTailer(TEST_GROUP, PIPE_DIRTY_OUT)) {
            assertEquals(null, tailer.read(Duration.ofSeconds(1)));
            theTestDoc.setPropertyValue("dc:title", "Dirty Document");
            session.saveDocument(theTestDoc);
            LogRecord<Record> record = tailer.read(Duration.ofSeconds(5));
            assertNotNull(record);
            BlobTextFromDocument dirtyDoc = fromRecord(record.message(), BlobTextFromDocument.class);
            assertEquals("Dirty Document", dirtyDoc.getProperties().get("dc:title"));
        }
    }

    @Test
    @ConditionalIgnoreRule.Ignore(condition = IgnoreKafka.class, cause = "AICORE-476")
    public void testBinaryText() throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "My binary Doc", "File");
        ((DocumentModelImpl) doc).setId(UUID.randomUUID().toString());
        LogManager manager = Framework.getService(StreamService.class).getLogManager();

        //First create some text and check it gets added to the stream
        try (LogTailer<Record> tailer = manager.createTailer(TEST_GROUP, DEFAULT_BINARY_TEXT)) {
            tailer.toEnd();
            doc = session.createDocument(doc);
            session.setDocumentSystemProp(doc.getRef(), BINARY_TEXT_SYS_PROP, "My text");
            session.save();
            txFeature.nextTransaction();
            LogRecord<Record> record = tailer.read(Duration.ofSeconds(5));
            assertNotNull(record);
            assertNotNull(record.message());
            BlobTextFromDocument andBack = fromRecord(record.message(), BlobTextFromDocument.class);
            assertEquals("My text", andBack.getProperties().get(NXQL.ECM_FULLTEXT));
        }

        //Create text twice but the second is ignore because its in the "window size"
        try (LogTailer<Record> tailer = manager.createTailer(TEST_GROUP, CUSTOM_BINARY_TEXT)) {
            tailer.toEnd();
            doc = session.createDocument(doc);
            session.setDocumentSystemProp(doc.getRef(), BINARY_TEXT_SYS_PROP, "My custom text");
            session.save();
            txFeature.nextTransaction();
            session.setDocumentSystemProp(doc.getRef(), BINARY_TEXT_SYS_PROP, "My custom text 2");
            session.save();
            txFeature.nextTransaction();
            LogRecord<Record> record = tailer.read(Duration.ofSeconds(5));
            assertNotNull(record);
            BlobTextFromDocument andBack = fromRecord(record.message(), BlobTextFromDocument.class);
            assertEquals("My custom text", andBack.getProperties().get(NXQL.ECM_FULLTEXT));
            record = tailer.read(Duration.ofSeconds(5));
            assertNull("The second record should be ignored because its inside the window", record);

        }
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
        assertEquals("Should not error even though nulls passed in", "ai/aname_void", buildName("aname", null, null));
        assertEquals("ai/king_bob_hope", buildName("king", "bob", "hope"));
        assertEquals("ai/king_bob_hope-rope", buildName("king", "bob", "hope,rope"));

        assertEquals("ai/king_bob", buildName("king", "bob", null));
        assertEquals("ai/king_void_kong", buildName("king", null, "kong"));

        assertEquals("ai/functionClass_stream-source_stream-sink1-stream-sink2",
                buildName("functionClass", "stream/source", "stream/sink1,stream/sink2"));

        assertEquals("ai/insight-custom_stream-source_stream-sink",
                buildName("insight.custom", "stream/source", "stream/sink"));
    }
}
