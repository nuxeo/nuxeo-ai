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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.nuxeo.PipesTestConfigFeature.PIPES_TEST_CONFIG;
import static org.nuxeo.runtime.stream.pipes.functions.Predicates.doc;
import static org.nuxeo.runtime.stream.pipes.functions.Predicates.docEvent;

import java.time.Duration;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.PipesTestConfigFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.lib.stream.log.LogRecord;
import org.nuxeo.lib.stream.log.LogTailer;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.StreamService;
import org.nuxeo.runtime.stream.pipes.consumers.LogAppenderConsumer;
import org.nuxeo.runtime.stream.pipes.functions.FilterFunction;
import org.nuxeo.runtime.stream.pipes.pipes.DocumentPipeFunction;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({PipesTestConfigFeature.class, PlatformFeature.class})
@Deploy({"org.nuxeo.runtime.stream", "org.nuxeo.runtime.stream.pipes.nuxeo-pipes",
        "org.nuxeo.runtime.stream.pipes.nuxeo-pipes:OSGI-INF/stream-pipes-test.xml"})
public class TestLogConsumer {

    @Inject
    protected PipelineService pipeService;

    @Inject
    CoreSession session;

    @Test
    public void TestLogConsumer() throws Exception {
        PipelineServiceImpl pipeServiceImpl = (PipelineServiceImpl) pipeService;
        LogAppenderConsumer logAppenderConsumer = pipeServiceImpl.addLogConsumer("test.log.consumer", 1);
        assertNotNull(logAppenderConsumer.toString());
        FilterFunction<Event, Record> function = new FilterFunction(docEvent(doc()), new DocumentPipeFunction());

        LogManager manager = Framework.getService(StreamService.class).getLogManager(PIPES_TEST_CONFIG);
        try (LogTailer<Record> tailer = manager.createTailer("group", "test.log.consumer")) {
            assertEquals(null, tailer.read(Duration.ofSeconds(1)));
            Record applied = function.apply(getTestEvent());
            if (applied != null) {
                logAppenderConsumer.accept(applied);
            }
            LogRecord<Record> record = tailer.read(Duration.ofSeconds(1));
            assertNotNull(record.message());
        }

        //Now check closing, it doesn't throw an error
        manager.close();
        logAppenderConsumer.getAppender();
    }

    @NotNull
    protected Event getTestEvent() {
        DocumentModel aDoc = session.createDocumentModel("/", "My Doc", "File");
        EventContextImpl evctx = new DocumentEventContext(session, session.getPrincipal(), aDoc);
        Event event = evctx.newEvent("myDocEvent");
        event.setInline(true);
        assertNotNull(event);
        return event;
    }
}
