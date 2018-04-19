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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.nuxeo.runtime.stream.pipes.functions.Predicates.doc;
import static org.nuxeo.runtime.stream.pipes.functions.Predicates.docEvent;
import static org.nuxeo.runtime.stream.pipes.functions.Predicates.event;
import static org.nuxeo.runtime.stream.pipes.functions.Predicates.hasFacets;
import static org.nuxeo.runtime.stream.pipes.functions.Predicates.isNotProxy;
import static org.nuxeo.runtime.stream.pipes.functions.Predicates.isPicture;

import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.metrics.NuxeoMetricSet;
import org.nuxeo.runtime.stream.pipes.functions.FilterFunction;
import org.nuxeo.runtime.stream.pipes.pipes.DocumentPipeFunction;
import org.nuxeo.runtime.stream.pipes.pipes.PicturePipeFunction;
import org.nuxeo.runtime.stream.pipes.services.PipelineService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({PlatformFeature.class})
@Deploy({"org.nuxeo.runtime.stream", "org.nuxeo.runtime.stream.pipes.nuxeo-pipes"})
public class EventPipesTest {

    @Inject
    CoreSession session;

    @Inject
    protected PipelineService pipeService;

    @Inject
    EventService eventService;

    @Test
    public void testFilterFunctions() {

        Event event = getTestEvent();
        NuxeoMetricSet funcMetric = new NuxeoMetricSet("nuxeo", "func", "test");

        FilterFunction<Event, Event> func = new FilterFunction<>(event(), e -> e);
        func.withMetrics(funcMetric);
        assertMetric(0, "nuxeo.func.test.supplied", funcMetric);
        assertEquals("Filter passed so must be an event", event, func.apply(event));
        assertMetric(1, "nuxeo.func.test.supplied", funcMetric);
        assertMetric(1, "nuxeo.func.test.transformed", funcMetric);

        func = new FilterFunction<>(event().and(Event::isPublic), e -> e);
        assertEquals("Filter passed so must be an event", event, func.apply(event));

        func = new FilterFunction<>(event().and(e -> !e.isImmediate()), e -> e);
        assertEquals("Filter passed so must be an event", event, func.apply(event));

        func = new FilterFunction<>(docEvent(doc()), e -> e);
        assertEquals("Filter passed so must be an event", event, func.apply(event));

        func = new FilterFunction<>(docEvent(isNotProxy().and(d -> d.getName().equals("My Doc"))), e -> e);
        assertEquals("Filter passed so must be My Doc", event, func.apply(event));
        func = new FilterFunction<>(docEvent(isNotProxy().and(hasFacets("Versionable", "Commentable"))), e -> e);
        assertEquals("Filter passed so must be an event", event, func.apply(event));

        func = new FilterFunction<>(docEvent(isNotProxy().and(hasFacets("Folderish"))), e -> e);
        func.withMetrics(funcMetric);
        assertMetric(0, "nuxeo.func.test.filterFailed", funcMetric);
        assertNull("Must not have folderish", func.apply(event));
        assertMetric(1, "nuxeo.func.test.filterFailed", funcMetric);
        func = new FilterFunction<>(docEvent(isNotProxy().and(hasFacets("Folderish").negate())), e -> e);
        assertEquals("Filter passed so must not have folderish", event, func.apply(event));

        func = new FilterFunction<>(docEvent(isNotProxy().and(isPicture())), e -> e);
        assertNull("It's not a picture", func.apply(event));
    }

    @Test
    public void testFunctions() {

        Event event = getTestEvent();
        FilterFunction<Event, Record> func = new PicturePipeFunction();
        assertNull("Its not a picture event", func.apply(event));
        assertNotNull("Must turn an event into a record", func.transformation.apply(event));
        func = new DocumentPipeFunction();
        assertNotNull("It is a document", func.apply(event));
    }

    @Test
    public void testEventPipes() throws InterruptedException {
        StringBuilder buffy = new StringBuilder();
        NuxeoMetricSet nuxeoMetricSet = new NuxeoMetricSet("nuxeo", "pipes", "test");
        FilterFunction<Event, String> func = new FilterFunction<>(event(), Event::getName);
        pipeService.addEventPipe("myDocEvent", nuxeoMetricSet, func, buffy::append);
        assertMetric(0, "nuxeo.pipes.test.events", nuxeoMetricSet);
        eventService.fireEvent(getTestEvent());
        eventService.waitForAsyncCompletion();
        assertEquals("myDocEvent", buffy.toString());
        assertMetric(1, "nuxeo.pipes.test.consumed", nuxeoMetricSet);
        assertMetric(1, "nuxeo.pipes.test.events", nuxeoMetricSet);
    }

    @Test
    public void TestBasicFunction() {
        FilterFunction<String, String> func = new FilterFunction<>(in -> true, t -> t);
        assertEquals("Hello World", func.apply("Hello World"));

        func = new FilterFunction<>(in -> true, String::toLowerCase);
        assertEquals("hello", func.apply("Hello"));

        func = new FilterFunction<>(in -> in.toLowerCase().startsWith("h"), t -> t);

        long matched = Stream.of("hello  ", "I", "am", "Happy  ", "hopefully  ").filter(func.filter).count();
        assertEquals(3, matched);

        StringBuffer ints = new StringBuffer();
        final FilterFunction<Integer, Integer> function = new FilterFunction<>(in -> in % 2 == 0, in -> in * in);

        IntStream.of(2, 3, 6, 1, 4).forEach(i -> {
            Integer applied = function.apply(i);
            if (applied != null) {
                ints.append(applied);
            }
        });
        assertEquals("43616", ints.toString());
    }

    @Test
    public void TestClassCast() {
        FilterFunction<String, Integer> ff = new FilterFunction<>(in -> true, null);
        assertNull(ff.apply("Hello"));
    }

    protected void assertMetric(int expected, String metric, NuxeoMetricSet metricSet) {
        assertEquals(expected, getMetricValue(metricSet, metric));
    }

    @SuppressWarnings("rawtypes")
	protected long getMetricValue(NuxeoMetricSet metricSet, String metric) {
        Map<String, Metric> metricMap = metricSet.getMetrics();
        Gauge g = (Gauge) metricMap.get(metric);
        return (Long) g.getValue();
    }

    @NotNull
    protected Event getTestEvent() {
        DocumentModel aDoc = session.createDocumentModel("/", "My Doc", "File");
        aDoc.addFacet("Publishable");
        aDoc.addFacet("Versionable");
        EventContextImpl evctx = new DocumentEventContext(session, session.getPrincipal(), aDoc);
        Event event = evctx.newEvent("myDocEvent");
        event.setInline(true);
        assertNotNull(event);
        return event;
    }
}
