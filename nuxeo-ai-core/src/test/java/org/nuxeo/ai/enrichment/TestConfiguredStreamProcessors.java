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
package org.nuxeo.ai.enrichment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_ITEMS;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_MODEL;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_SCHEMA_NAME;
import static org.nuxeo.ai.enrichment.EnrichmentTestFeature.FILE_CONTENT;
import static org.nuxeo.ai.enrichment.EnrichmentTestFeature.PIPES_TEST_CONFIG;
import static org.nuxeo.ai.enrichment.EnrichmentTestFeature.blobTestImage;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toRecord;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.platform.tag.TagService;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.log.LogAppender;
import org.nuxeo.lib.stream.log.LogLag;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.lib.stream.log.LogOffset;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.metrics.MetricsService;
import org.nuxeo.runtime.stream.StreamService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;

/**
 * Tests a fully configured stream processor
 */
@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, PlatformFeature.class})
@Deploy({"org.nuxeo.ecm.platform.tag", "org.nuxeo.ecm.automation.core",
        "org.nuxeo.ai.ai-core:OSGI-INF/stream-test.xml"})
public class TestConfiguredStreamProcessors {

    protected static final String METRICS_PREFIX = "nuxeo.ai.enrichment.test_images$simpleTest$test_images.out.";

    @Inject
    protected CoreSession session;

    @Inject
    protected TagService tagService;

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected BlobManager blobManager;

    @Test
    public void testConfiguredStreamProcessor() throws Exception {

        //Create a document
        DocumentModel testDoc = session.createDocumentModel("/", "My Doc", "File");
        testDoc = session.createDocument(testDoc);
        String docId = testDoc.getId();
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        //Create metadata about the blob and document
        BlobTextFromDocument blobTextFromDoc = new BlobTextFromDocument();
        blobTextFromDoc.setId(docId);
        blobTextFromDoc.setRepositoryName(testDoc.getRepositoryName());
        blobTextFromDoc.addBlob(FILE_CONTENT, new BlobMetaImpl("test", "image/jpeg", "xyx", "xyz", null, 45L));

        //Check metrics, nothing produced
        Map<String, Gauge> gauges = getMetrics(METRICS_PREFIX);
        Gauge called = gauges.get(METRICS_PREFIX + "called");
        Gauge produced = gauges.get(METRICS_PREFIX + "produced");
        assertEquals("The provider should not be called yet.", 0L, called.getValue());
        assertEquals(0L, produced.getValue());

        //Now check and append a Record to the "test_images" stream
        LogManager manager = Framework.getService(StreamService.class).getLogManager(PIPES_TEST_CONFIG);
        LogAppender<Record> appender = manager.getAppender("test_images");
        LogLag lag = manager.getLag("test_images.out", "test_images.out$SaveEnrichmentFunction");
        assertEquals("There should be nothing waiting to be processed", 0, lag.lag());
        LogOffset offset = appender.append("mykey", toRecord("k", blobTextFromDoc));
        waitForNoLag(manager, "test_images.out", "test_images.out$RaiseEnrichmentEvent", Duration.ofSeconds(5));

        //After waiting for the appender lets check the 1 record was read
        assertEquals("We must have been called once", 1L, called.getValue());
        assertEquals("We must have produced one record", 1L, produced.getValue());
        lag = manager.getLag("test_images.out", "test_images.out$SaveEnrichmentFunction");
        assertEquals("All records should be processed", 0, lag.lag());

        //Confirm the document was enriched with the metadata
        txFeature.nextTransaction();
        DocumentModel enrichedDoc = session.getDocument(new IdRef(docId));
        assertTrue("The document must have the enrichment facet", enrichedDoc.hasFacet(ENRICHMENT_FACET));
        Property classProp = enrichedDoc.getPropertyObject(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS);
        Assert.assertNotNull(classProp);
        assertEquals("simpleTest", classProp.get(0).get(ENRICHMENT_MODEL).getValue());

        //Confirm event listeners fired
        String description = (String) enrichedDoc.getPropertyValue("dc:description");
        assertEquals("The metaListening chain must have fired and set the description",
                     "I_AM_LISTENING", description);
        String title = (String) enrichedDoc.getPropertyValue("dc:title");
        assertEquals("metadataListener.groovy must have set the title",
                     "George Paul", title);

        //Confirm 5 tags were added
        Set<String> tags = tagService.getTags(session, docId);
        assertEquals(5, tags.size());
    }

    protected Map<String, Gauge> getMetrics(String metricPrefix) {
        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());
        return registry.getGauges().entrySet().stream()
                       .filter(e -> e.getKey().startsWith(metricPrefix))
                       .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Test
    public void testCaching() throws IOException, InterruptedException {

        // Setup test data.
        DocumentModel testDoc = session.createDocumentModel("/", "My caching Doc", "File");
        testDoc = session.createDocument(testDoc);
        String docId = testDoc.getId();
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        BlobTextFromDocument blobTextFromDoc = blobTestImage(blobManager);
        blobTextFromDoc.setId(docId);
        blobTextFromDoc.setRepositoryName(testDoc.getRepositoryName());
        blobTextFromDoc.getBlobs().get(FILE_CONTENT).setDigest("myUniqueDigest");
        Record record = toRecord("c1", blobTextFromDoc);

        // Append the record and check the results
        LogManager manager = Framework.getService(StreamService.class).getLogManager(PIPES_TEST_CONFIG);
        LogAppender<Record> appender = manager.getAppender("test_images");
        appender.append("cache1", record);
        waitForNoLag(manager, "test_images.out", "test_images.out$RaiseEnrichmentEvent", Duration.ofSeconds(5));

        Map<String, Gauge> gauges = getMetrics(METRICS_PREFIX);
        Gauge cached = gauges.get(METRICS_PREFIX + "cacheHit");
        long cacheHit = (long) cached.getValue();

        appender.append("cache2", record);
        appender.append("cache3", record);
        waitForNoLag(manager, "test_images.out", "test_images.out$RaiseEnrichmentEvent", Duration.ofSeconds(5));

        assertEquals(cacheHit + 2, cached.getValue());
    }

    @Test
    public void testSuggestConsumer() throws IOException, InterruptedException {

        BlobTextFromDocument blobTextFromDoc = blobTestImage(blobManager);
        blobTextFromDoc.setId("mydocId");
        blobTextFromDoc.setRepositoryName("text");
        List<EnrichmentMetadata.Label> labels = Arrays.asList(new AIMetadata.Label("girl", 0.5f, 0L),
                new AIMetadata.Label("boy", 0.4f, 0L));
        LabelSuggestion labelSuggestion = new LabelSuggestion("my:property", labels);
        EnrichmentMetadata EnrichmentMetadata = new EnrichmentMetadata.Builder(Instant.now(), "m1", "stest",
                blobTextFromDoc).withLabels(Collections.singletonList(labelSuggestion))
                                .withDigest("blobxx")
                                .withDigest("fredblogs")
                                .withCreator("bob")
                                .withRawKey("xyz")
                                .build();
        Map<String, Gauge> gauges = getMetrics("nuxeo.ai.streams.func.test_suggestion$CustomSuggestionConsumer.");
        Gauge call = gauges.get("nuxeo.ai.streams.func.test_suggestion$CustomSuggestionConsumer." + "called");
        long called = (long) call.getValue();
        assertEquals(0, called);
        LogManager manager = Framework.getService(StreamService.class).getLogManager(PIPES_TEST_CONFIG);
        LogAppender<Record> appender = manager.getAppender("test_suggestion");
        appender.append("suggest1", toRecord("s1", EnrichmentMetadata));
        appender.append("suggest2", toRecord("s2", EnrichmentMetadata));
        waitForNoLag(manager, "test_suggestion", "test_suggestion$CustomSuggestionConsumer", Duration.ofSeconds(5));
        assertEquals(2L, call.getValue());
    }

    /**
     * Wait until there is no lag or timesout.
     * This is a temporary solution until this logic is available in the framework.
     */
    public static void waitForNoLag(LogManager manager, String name, String group, Duration timeout) throws InterruptedException {

        final long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(1000);
            LogLag lag = manager.getLag(name, group);
            if (lag.upper() == 1 && lag.lag() == 0) {
                return;
            }
        }
    }
}
