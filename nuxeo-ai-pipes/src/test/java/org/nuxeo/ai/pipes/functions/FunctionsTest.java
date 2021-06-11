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
package org.nuxeo.ai.pipes.functions;

import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.nuxeo.ai.pipes.events.EventPipesTest.TEST_MIME_TYPE;
import static org.nuxeo.ai.pipes.events.EventPipesTest.getTestEvent;
import static org.nuxeo.ai.pipes.functions.Predicates.doc;
import static org.nuxeo.ai.pipes.functions.Predicates.docEvent;
import static org.nuxeo.ai.pipes.functions.Predicates.event;
import static org.nuxeo.ai.pipes.functions.Predicates.hasFacets;
import static org.nuxeo.ai.pipes.functions.Predicates.isNotProxy;
import static org.nuxeo.ai.pipes.functions.Predicates.isPicture;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.pipes.filters.DocumentEventFilter;
import org.nuxeo.ai.pipes.filters.DocumentPathFilter;
import org.nuxeo.ai.pipes.filters.FacetFilter;
import org.nuxeo.ai.pipes.filters.Filter;
import org.nuxeo.ai.pipes.filters.NoVersionFilter;
import org.nuxeo.ai.pipes.filters.PrimaryTypeFilter;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy({ "org.nuxeo.runtime.stream", "org.nuxeo.ai.nuxeo-ai-pipes" })
public class FunctionsTest {

    @Inject
    CoreSession session;

    @Test
    public void testFilterFunctions() throws Exception {

        Event event = getTestEvent(session);

        PreFilterFunction<Event, Event> func = new PreFilterFunction<>(event(), e -> e);
        assertEquals("Filter passed so must be an event", event, func.apply(event));

        func = new PreFilterFunction<>(event().and(Event::isPublic), e -> e);
        assertEquals("Filter passed so must be an event", event, func.apply(event));

        func = new PreFilterFunction<>(event().and(e -> !e.isImmediate()), e -> e);
        assertEquals("Filter passed so must be an event", event, func.apply(event));

        func = new PreFilterFunction<>(docEvent(doc()), e -> e);
        assertEquals("Filter passed so must be an event", event, func.apply(event));

        func = new PreFilterFunction<>(docEvent(isNotProxy().and(d -> d.getName().equals("My Doc"))), e -> e);
        assertEquals("Filter passed so must be My Doc", event, func.apply(event));
        func = new PreFilterFunction<>(docEvent(isNotProxy().and(hasFacets("Versionable", "Commentable"))), e -> e);
        assertEquals("Filter passed so must be an event", event, func.apply(event));

        func = new PreFilterFunction<>(docEvent(isNotProxy().and(hasFacets("Folderish"))), e -> e);
        assertNull("Must not have folderish", func.apply(event));
        func = new PreFilterFunction<>(docEvent(isNotProxy().and(hasFacets("Folderish").negate())), e -> e);
        assertEquals("Filter passed so must not have folderish", event, func.apply(event));

        func = new PreFilterFunction<>(docEvent(isNotProxy().and(isPicture())), e -> e);
        assertNull("It's not a picture", func.apply(event));

        func = new PreFilterFunction<>(in -> true, s -> {
            throw new NuxeoException("Invalid");
        });
        assertNull(func.apply(event));
    }

    @Test
    public void testBasicFunction() {
        PreFilterFunction<String, String> func = filterFunc(in -> true, t -> t);
        assertEquals("Hello World", func.apply("Hello World"));

        func = filterFunc(in -> true, String::toLowerCase);
        assertEquals("hello", func.apply("Hello"));

        func = filterFunc(in -> in.toLowerCase().startsWith("h"), t -> t);

        long matched = Stream.of("hello  ", "I", "am", "Happy  ", "hopefully  ").filter(func.filter).count();
        assertEquals(3, matched);

        StringBuffer ints = new StringBuffer();
        final PreFilterFunction<Integer, Integer> function = new PreFilterFunction<>(in -> in % 2 == 0, in -> in * in);

        IntStream.of(2, 3, 6, 1, 4).forEach(i -> {
            Integer applied = function.apply(i);
            if (applied != null) {
                ints.append(applied);
            }
        });
        assertEquals("43616", ints.toString());
    }

    @Test
    public void testFunctions() throws Exception {
        Event event = getTestEvent(session);
        PreFilterFunction<Event, Collection<Record>> func = new PropertiesToStream();

        try {
            func.init(Collections.emptyMap());
            fail();
        } catch (IllegalArgumentException ignored) {
        }

        FacetFilter facetFilter = new FacetFilter();
        facetFilter.init(Collections.singletonMap("includedFacets", "Picture"));
        func.filter = docEvent(facetFilter);
        func.init(Collections.singletonMap("textProperties", "dc:creator"));
        assertNull("Its not a picture event", func.apply(event));
        func.filter = docEvent(d -> true);
        Collection<Record> applied = func.apply(event);
        assertEquals("It is a document with a creator", 1, applied.size());
        facetFilter.init(Collections.singletonMap("excludedFacets", "Versionable,Commentable"));
        func.filter = docEvent(facetFilter);
        assertNull("Versionable are excluded.", func.apply(event));
        facetFilter.init(Collections.singletonMap("includedFacets", "Versionable"));
        applied = func.apply(event);
        assertEquals("Versionable are included", 1, applied.size());

        DocumentPathFilter pathFilter = new DocumentPathFilter();
        pathFilter.init(Collections.singletonMap("endsWith", "Doc"));
        func.filter = docEvent(pathFilter);
        applied = func.apply(event);
        assertEquals("Paths ends with Doc", 1, applied.size());
        Map<String, String> options = new HashMap<>();
        options.put("startsWith", "/My");
        options.put("contains", "My Doc");
        pathFilter.init(options);
        applied = func.apply(event);
        assertEquals("Paths contains Doc", 1, applied.size());
        options.clear();
        options.put("pattern", "NO_MATCH");
        pathFilter.init(options);
        applied = func.apply(event);
        assertNull("Pattern does not match", func.apply(event));
        options.put("pattern", ".*\\s+\\w{3}");
        pathFilter.init(options);
        applied = func.apply(event);
        assertEquals("Pattern now matches", 1, applied.size());

        // Test PrimaryTypeFilter
        PrimaryTypeFilter typeFilter = new PrimaryTypeFilter();
        typeFilter.init(Collections.singletonMap("isType", "Picture"));
        func.filter = docEvent(typeFilter);
        func.init(Collections.singletonMap("textProperties", "dc:creator"));
        assertNull("Its not a picture", func.apply(event));
        typeFilter.init(Collections.singletonMap("isType", "File"));
        func.filter = docEvent(typeFilter);
        applied = func.apply(event);
        assertEquals("It is a file", 1, applied.size());
    }

    @Test
    public void shouldApplyNoVersionFilter() {
        PreFilterFunction<Event, Collection<Record>> func = new PropertiesToStream();
        func.init(Collections.singletonMap("textProperties", "dc:creator"));

        NoVersionFilter noVersionFilter = new NoVersionFilter();
        func.filter = docEvent(noVersionFilter);

        DocumentModel doc = session.createDocumentModel("/", "MyDoc", "File");
        assertNotNull(doc);

        doc.addFacet("Publishable");
        doc.addFacet("Versionable");
        Blob blob = Blobs.createBlob("My text", TEST_MIME_TYPE);
        doc.setPropertyValue("file:content", (Serializable) blob);
        doc = session.createDocument(doc);

        EventContextImpl evctx = new DocumentEventContext(session, session.getPrincipal(), doc);
        Event event = evctx.newEvent("myDocEvent");
        event.setInline(true);

        Collection<Record> result = func.apply(event);
        assertThat(result).hasSize(1);

        DocumentRef verRef = session.checkIn(doc.getRef(), VersioningOption.MAJOR, "bump");
        DocumentModel versionDoc = session.getDocument(verRef);

        assertThat(versionDoc.isVersion()).isTrue();

        evctx = new DocumentEventContext(session, session.getPrincipal(), versionDoc);
        event = evctx.newEvent("myDocEvent");
        event.setInline(true);

        result = func.apply(event);
        assertThat(result).isNullOrEmpty();
    }

    @Test
    public void testClassCast() {
        PreFilterFunction<String, Integer> ff = new PreFilterFunction<>(in -> true, null);
        assertNull(ff.apply("Hello"));
    }

    @Test
    public void testBuilder() {
        Filter.EventFilter eventFilter = new DocumentEventFilter.Builder().build();
        assertNotNull(eventFilter);

        eventFilter = new DocumentEventFilter.Builder().withDocumentFilter(d -> d.hasSchema("schema")).build();

        assertNotNull(eventFilter);
    }

    public static <T, R> PreFilterFunction<T, R> filterFunc(Predicate<? super T> filter,
            Function<? super T, ? extends R> transformation) {
        return new PreFilterFunction<>(filter, transformation);
    }
}
