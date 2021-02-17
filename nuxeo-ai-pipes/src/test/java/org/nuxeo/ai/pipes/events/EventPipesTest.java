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
package org.nuxeo.ai.pipes.events;

import io.dropwizard.metrics5.Gauge;
import io.dropwizard.metrics5.Metric;
import io.dropwizard.metrics5.MetricName;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.pipes.PipesTestConfigFeature;
import org.nuxeo.ai.pipes.consumers.LogAppenderConsumer;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.sdk.objects.PropertyType;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelFactory;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.ecm.core.event.impl.EventServiceImpl;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.metrics.NuxeoMetricSet;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.pipes.services.JacksonUtil.fromRecord;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toRecord;

@RunWith(FeaturesRunner.class)
@Features({ PipesTestConfigFeature.class, PlatformFeature.class })
@Deploy({ "org.nuxeo.runtime.stream", "org.nuxeo.ai.nuxeo-ai-pipes",
        "org.nuxeo.ai.nuxeo-ai-pipes:OSGI-INF/stream-pipes-test.xml" })
public class EventPipesTest {

    public static final String TEST_MIME_TYPE = "text/plain";

    @Inject
    protected EventService eventService;

    @Inject
    CoreSession session;

    public static Event getTestEvent(CoreSession session) throws Exception {
        DocumentModel doc = session.createDocumentModel("/", "My Doc", "File");
        ((DocumentModelImpl) doc).setId(UUID.randomUUID().toString());
        doc.addFacet("Publishable");
        doc.addFacet("Versionable");
        Blob blob = Blobs.createBlob("My text", TEST_MIME_TYPE);
        doc.setPropertyValue("file:content", (Serializable) blob);
        return getTestEvent(session, doc, "myDocEvent");
    }

    public static Event getTestEvent(CoreSession session, DocumentModel doc, String eventName) {
        doc = session.createDocument(doc);
        session.save();
        EventContextImpl evctx = new DocumentEventContext(session, session.getPrincipal(), doc);
        Event event = evctx.newEvent(eventName);
        event.setInline(true);
        assertNotNull(event);
        return event;
    }

    public static void assertMetric(int expected, String metric, NuxeoMetricSet metricSet) {
        assertEquals(expected, getMetricValue(metricSet, metric));
    }

    @SuppressWarnings("rawtypes")
    public static long getMetricValue(NuxeoMetricSet metricSet, String metric) {
        Map<MetricName, Metric> metricMap = metricSet.getMetrics();
        Gauge g = (Gauge) metricMap.get(metric);
        return (Long) g.getValue();
    }

    @Test
    public void testConsumer() {
        LogAppenderConsumer consumer = new LogAppenderConsumer(null);
        assertNotNull("toString shouldn't throw a null pointer even if the appender is null", consumer.toString());
    }

    @Test
    public void testDocEventToStream() throws Exception {
        DocEventToStream doc2stream = new DocEventToStream();
        Collection<BlobTextFromDocument> result = doc2stream.apply(
                getTestEvent(session, DocumentModelFactory.createDocumentModel("File"), "anyEvent"));
        assertNotNull(result);
        assertEquals("Nothing in the test event to serialize", 0, result.size());

        Event testEvent = getTestEvent(session);
        result = doc2stream.apply(testEvent);
        assertEquals("There is 1 blob", 1, result.size());
        assertTrue(
                result.iterator().next().computePropertyBlobs().containsKey(new PropertyType("file:content", "img")));

        List<String> unknownProp = singletonList("dublinapple:core");

        result = new DocEventToStream(DocEventToStream.DEFAULT_BLOB_PROPERTIES, unknownProp, null).apply(testEvent);
        assertEquals("2 properties were specified by only 1 is valid", 1, result.size());

        List<PropertyType> unknownPropBlob = singletonList(new PropertyType("dublinapple:core", "txt"));
        result = new DocEventToStream(unknownPropBlob, null, null).apply(testEvent);
        assertEquals("No valid properties were specified so no results are returned", 0, result.size());

        List<String> creator = singletonList("dc:creator");
        doc2stream = new DocEventToStream(null, null, creator);
        List<BlobTextFromDocument> validResult = (List<BlobTextFromDocument>) doc2stream.apply(testEvent);
        assertEquals("There is 1 blob", 1, validResult.size());
        assertEquals("Administrator", validResult.get(0).getProperties().get("dc:creator"));

        doc2stream = new DocEventToStream(null, creator, null);
        validResult = (List<BlobTextFromDocument>) doc2stream.apply(testEvent);
        assertEquals("There is 1 blob", 1, validResult.size());
        assertEquals("Administrator", validResult.get(0).getProperties().get("dc:creator"));

        doc2stream = new DocEventToStream(DocEventToStream.DEFAULT_BLOB_PROPERTIES, creator,
                singletonList("dc:modified"));
        validResult = (List<BlobTextFromDocument>) doc2stream.apply(testEvent);
        BlobTextFromDocument firstResult = validResult.get(0);
        assertNotNull(firstResult.getProperties().get("dc:modified"));
        ManagedBlob blob = firstResult.computePropertyBlobs().get(new PropertyType("file:content", "img"));
        assertNotNull(blob);
        assertEquals("1 blob and 1 text", 2, validResult.size());

        BlobTextFromDocument andBackAgain = fromRecord(toRecord("k", firstResult), BlobTextFromDocument.class);
        assertEquals(firstResult, andBackAgain);
    }

    @Test
    public void testPostCommitEventRegistration() {
        EventServiceImpl eventS = (EventServiceImpl) eventService;
        assertTrue("We must add a PostCommit listener via config.", eventS.getEventListenerList()
                                                                          .getAsyncPostCommitListeners()
                                                                          .stream()
                                                                          .anyMatch(
                                                                                  l -> l instanceof PostCommitEventListenerWrapper));
    }

}
