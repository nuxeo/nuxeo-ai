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
package org.nuxeo.runtime.stream.pipes.events;

import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.nuxeo.runtime.stream.pipes.functions.Predicates.doc;
import static org.nuxeo.runtime.stream.pipes.functions.Predicates.docEvent;
import static org.nuxeo.runtime.stream.pipes.functions.Predicates.event;
import static org.nuxeo.runtime.stream.pipes.functions.Predicates.hasFacets;
import static org.nuxeo.runtime.stream.pipes.functions.Predicates.isNotProxy;
import static org.nuxeo.runtime.stream.pipes.functions.Predicates.isPicture;
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.fromRecord;
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.toRecord;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelFactory;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
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
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;
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

    public static final String TEST_MIME_TYPE = "text/plain";
    @Inject
    protected PipelineService pipeService;
    @Inject
    CoreSession session;
    @Inject
    EventService eventService;

    public static Event getTestEvent(CoreSession session) throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "My Doc", "File");
        ((DocumentModelImpl) doc).setId(UUID.randomUUID().toString());
        doc.addFacet("Publishable");
        doc.addFacet("Versionable");
        Blob blob = Blobs.createBlob("My text", TEST_MIME_TYPE);
        doc.setPropertyValue("file:content", (Serializable) blob);
        return getTestEvent(session, doc);
    }

    public static Event getTestEvent(CoreSession session, DocumentModel doc) {
        doc = session.createDocument(doc);
        session.save();
        EventContextImpl evctx = new DocumentEventContext(session, session.getPrincipal(), doc);
        Event event = evctx.newEvent("myDocEvent");
        event.setInline(true);
        assertNotNull(event);
        return event;
    }

    @Test
    public void testFilterFunctions() throws Exception {

        Event event = getTestEvent(session);
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

        func = new FilterFunction<>(in -> true, s -> {
            throw new NuxeoException("Invalid");
        } );
        func.withMetrics(funcMetric);
        assertMetric(0, "nuxeo.func.test.errors", funcMetric);
        func.apply(event);
        assertMetric(1, "nuxeo.func.test.errors", funcMetric);
    }

    @Test
    public void testFunctions() throws Exception {

        Event event = getTestEvent(session);
        FilterFunction<Event, Collection<Record>> func = new PicturePipeFunction();
        assertNull("Its not a picture event", func.apply(event));
        Collection<Record> applied = func.transformation.apply(event);
        assertTrue("Must turn an event into a record", applied.size() == 1);
        func = new DocumentPipeFunction();
        applied = func.apply(event);
        assertTrue("It is a document", applied.size() == 1);
    }

    @Test
    public void testEventPipes() throws Exception {
        StringBuilder buffy = new StringBuilder();
        NuxeoMetricSet nuxeoMetricSet = new NuxeoMetricSet("nuxeo", "pipes", "test");
        FilterFunction<Event, Collection<String>> func = new FilterFunction<>(event(), f -> singletonList(f.getName()));
        pipeService.addEventPipe("myDocEvent", nuxeoMetricSet, func, buffy::append);
        assertMetric(0, "nuxeo.pipes.test.events", nuxeoMetricSet);
        eventService.fireEvent(getTestEvent(session));
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
    public void TestDocEventToStream() throws Exception {
        DocEventToStream doc2stream = new DocEventToStream();
        Collection<BlobTextStream> result =
                doc2stream.apply(getTestEvent(session, DocumentModelFactory.createDocumentModel("File")));
        assertNotNull(result);
        assertEquals("Nothing in the test event to serialize", 0, result.size());

        Event testEvent = getTestEvent(session);
        result = doc2stream.apply(testEvent);
        assertEquals("There is 1 blob", 1, result.size());
        assertEquals("file:content", result.iterator().next().getXPaths().get(0));

        List<String> unknownProp = Collections.singletonList("dublinapple:core");

        try {
            new DocEventToStream(DocEventToStream.DEFAULT_BLOB_PROPERTIES, unknownProp, null).apply(testEvent);
            fail();
        } catch (PropertyNotFoundException ignored) {
        }

        try {
            new DocEventToStream(unknownProp, null, null).apply(testEvent);
            fail();
        } catch (PropertyNotFoundException ignored) {
        }

        try {
            new DocEventToStream(null, null, unknownProp).apply(testEvent);
            fail();
        } catch (PropertyNotFoundException ignored) {
        }

        List<String> creator = Collections.singletonList("dc:creator");
        doc2stream = new DocEventToStream(null, null, creator);
        List<BlobTextStream> validResult = (List<BlobTextStream>) doc2stream.apply(testEvent);
        assertEquals("There is 1 blob", 1, validResult.size());
        assertEquals("dc:creator", validResult.get(0).getXPaths().get(0));
        assertEquals("Administrator", validResult.get(0).getProperties().get("dublincore").get("creator"));

        doc2stream = new DocEventToStream(null, creator, null);
        validResult = (List<BlobTextStream>) doc2stream.apply(testEvent);
        assertEquals("There is 1 blob", 1, validResult.size());
        assertEquals("dc:creator", validResult.get(0).getXPaths().get(0));
        assertEquals("Administrator", validResult.get(0).getText());

        doc2stream = new DocEventToStream(DocEventToStream.DEFAULT_BLOB_PROPERTIES, creator, Arrays.asList("dc:modified"));
        validResult = (List<BlobTextStream>) doc2stream.apply(testEvent);
        BlobTextStream firstResult = validResult.get(0);
        assertEquals("file:content", firstResult.getXPaths().get(1));
        assertEquals("dc:modified", firstResult.getXPaths().get(0));
        assertEquals("1 blob and 1 text", 2, validResult.size());

        BlobTextStream andBackAgain = fromRecord(toRecord("k", firstResult), BlobTextStream.class);
        assertEquals(firstResult, andBackAgain);
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
}
