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

import java.util.Arrays;
import java.util.Map;
import java.util.stream.IntStream;

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
import org.nuxeo.runtime.metrics.NuxeoMetricSet;
import org.nuxeo.runtime.stream.pipes.functions.FilterFunction;
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

        FilterFunction func = new FilterFunction<Event, Event>(event(), e -> e);
        func.withMetrics(funcMetric);
        assertMetric(0, "nuxeo.func.test.supplied", funcMetric);
        assertEquals("Filter passed so must be an event", event, func.apply(event));
        assertMetric(1, "nuxeo.func.test.supplied", funcMetric);
        assertMetric(1, "nuxeo.func.test.transformed", funcMetric);

        func = new FilterFunction<Event, Event>(event().and(e -> e.isPublic()), e -> e);
        assertEquals("Filter passed so must be an event", event, func.apply(event));

        func = new FilterFunction<Event, Event>(event().and(e -> !e.isImmediate()), e -> e);
        assertEquals("Filter passed so must be an event", event, func.apply(event));

        func = new FilterFunction<Event, Event>(docEvent(doc()), e -> e);
        assertEquals("Filter passed so must be an event", event, func.apply(event));

        func = new FilterFunction<Event, Event>(docEvent(isNotProxy()
                                                                 .and(d -> d.getName().equals("My Doc"))),
                                                e -> e
        );
        assertEquals("Filter passed so must be My Doc", event, func.apply(event));
        func = new FilterFunction<Event, Event>(docEvent(isNotProxy()
                                                                 .and(hasFacets("Versionable", "Commentable"))),
                                                e -> e
        );
        assertEquals("Filter passed so must be an event", event, func.apply(event));

        func = new FilterFunction<Event, Event>(docEvent(isNotProxy()
                                                                 .and(hasFacets("Folderish"))),
                                                e -> e
        );
        func.withMetrics(funcMetric);
        assertMetric(0, "nuxeo.func.test.filterFailed", funcMetric);
        assertNull("Must not have folderish", func.apply(event));
        assertMetric(1, "nuxeo.func.test.filterFailed", funcMetric);
        func = new FilterFunction<Event, Event>(docEvent(isNotProxy()
                                                                 .and(hasFacets("Folderish").negate())),
                                                e -> e
        );
        assertEquals("Filter passed so must not have folderish", event, func.apply(event));

        func = new FilterFunction<Event, Event>(docEvent(isNotProxy()
                                                                 .and(isPicture())),
                                                e -> e
        );
        assertNull("It's not a picture", func.apply(event));
    }

    @Test
    public void testEventPipes() throws InterruptedException {
        StringBuffer buffy = new StringBuffer();
        NuxeoMetricSet nuxeoMetricSet = new NuxeoMetricSet("nuxeo", "pipes", "test");
        FilterFunction func = new FilterFunction<Event, String>(event(), Event::getName);
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
        FilterFunction func = new FilterFunction<String, String>(in -> {
            return true;
        }, t -> t);
        assertEquals("Hello World", func.apply("Hello World"));

        func = new FilterFunction<String, String>(in -> {
            return true;
        }, t -> t.toLowerCase());
        assertEquals("hello", func.apply("Hello"));

        final StringBuffer buffer = new StringBuffer();
        func = new FilterFunction<String, String>(in -> {
            return in.toLowerCase().startsWith("h");
        },
                                                  t -> {
                                                      return t;
                                                  }
        );

        long matched = Arrays.asList("hello  ", "I", "am", "Happy  ", "hopefully  ").stream().filter(func.filter)
                             .count();
        assertEquals(3, matched);

        StringBuffer ints = new StringBuffer();
        final FilterFunction<Integer, Integer> function = new FilterFunction<Integer, Integer>
                (in -> {
                    return in % 2 == 0;
                },
                 in -> {
                     return in * in;
                 }
                );

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
        FilterFunction ff = new FilterFunction<String, Integer>(in -> {
            return true;
        },
                                                                null
        );
        assertNull(ff.apply("Hello"));
    }

    protected void assertMetric(int expected, String metric, NuxeoMetricSet metricSet) {
        assertEquals(expected, getMetricValue(metricSet, metric));
    }

    protected long getMetricValue(NuxeoMetricSet metricSet, String metric) {
        Map<String, Metric> metricMap = metricSet.getMetrics();
        Gauge g = (Gauge) metricMap.get(metric);
        return (Long) g.getValue();
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
