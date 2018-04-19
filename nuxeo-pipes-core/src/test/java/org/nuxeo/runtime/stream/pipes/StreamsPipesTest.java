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
import static org.nuxeo.PipesTestConfigFeature.PIPES_TEST_CONFIG;
import static org.nuxeo.runtime.stream.pipes.streams.FunctionStreamProcessor.getStreamsList;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.PipesTestConfigFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.lib.stream.log.LogRecord;
import org.nuxeo.lib.stream.log.LogTailer;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.StreamService;
import org.nuxeo.runtime.stream.pipes.streams.FunctionStreamProcessor;
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

        Event event = getTestEvent();
        LogManager manager = Framework.getService(StreamService.class).getLogManager(PIPES_TEST_CONFIG);
        try (LogTailer<Record> tailer = manager.createTailer("group", "text.out")) {
            assertEquals(null, tailer.read(Duration.ofSeconds(1)));
            eventService.fireEvent(event);
            eventService.waitForAsyncCompletion();
            LogRecord<Record> record = tailer.read(Duration.ofSeconds(1));
            assertNotNull(record.message());
        }
    }

    @NotNull
    protected Event getTestEvent() {
        DocumentModel aDoc = session.createDocumentModel("/", "My Doc", "File");
        session.createDocument(aDoc);
        session.save();
        EventContextImpl evctx = new DocumentEventContext(session, session.getPrincipal(), aDoc);
        Event event = evctx.newEvent("myDocEvent");
        event.setInline(true);
        assertNotNull(event);
        return event;
    }

    @Test
    public void testStreamsList() {
        FunctionStreamProcessor processor = new FunctionStreamProcessor();
        try {
            getStreamsList(Collections.emptyMap());
            assertTrue("The call should have failed", false);
        } catch (IllegalArgumentException ignored) {
        }

        Map<String, String> options = new HashMap<>();
        options.put(FunctionStreamProcessor.LOG_IN, "bob");
        List<String> streams = getStreamsList(options);
        assertEquals(1, streams.size());
        assertEquals("i1:bob", streams.get(0));

        options.put(FunctionStreamProcessor.LOG_OUT, "hope");
        streams = getStreamsList(options);
        assertNotNull(streams);
        assertEquals("i1:bob", streams.get(0));
        assertEquals("o1:hope", streams.get(1));

        options.put(FunctionStreamProcessor.LOG_OUT, "hope,nope,rope");
        streams = getStreamsList(options);
        assertNotNull(streams);
        assertEquals("o1:hope", streams.get(1));
        assertEquals("o2:nope", streams.get(2));
        assertEquals("o3:rope", streams.get(3));
    }
}
