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
import static org.nuxeo.ai.AIConstants.AI_SERVICE_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_CLASSIFICATIONS;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_NAME;
import static org.nuxeo.ai.enrichment.EnrichmentTestFeature.PIPES_TEST_CONFIG;
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.toRecord;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.test.TransactionalFeature;
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
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.inject.Inject;

/**
 * Tests a fully configured stream processor
 */
@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, PlatformFeature.class})
@Deploy({"org.nuxeo.ecm.platform.tag", "org.nuxeo.ai.ai-core:OSGI-INF/stream-test.xml"})
public class TestConfiguredStreamProcessors {

    @Inject
    protected CoreSession session;

    @Inject
    protected TagService tagService;

    @Inject
    protected TransactionalFeature txFeature;

    @Test
    public void testConfiguredStreamProcessor() throws Exception {

        DocumentModel testDoc = session.createDocumentModel("/", "My Doc", "File");
        testDoc = session.createDocument(testDoc);
        String docId = testDoc.getId();
        session.save();
        txFeature.nextTransaction();
        BlobTextStream blobTextStream = new BlobTextStream();
        blobTextStream.setId(docId);
        blobTextStream.setRepositoryName(testDoc.getRepositoryName());
        blobTextStream.setBlob(new BlobMetaImpl("test", "image/jpeg", "xyx", "xyz", null, 45L));
        Record record = toRecord("k", blobTextStream);
        String metricPrefix = "nuxeo.streams.enrichment.test_images>simpleTest>test_images.out.";
        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());
        Map<String, Gauge> gauges = registry.getGauges().entrySet().stream()
                                            .filter(e -> e.getKey().startsWith(metricPrefix))
                                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Gauge called = gauges.get(metricPrefix + "called");
        Gauge produced = gauges.get(metricPrefix + "produced");
        assertEquals(0L, called.getValue());
        assertEquals(0L, produced.getValue());
        LogManager manager = Framework.getService(StreamService.class).getLogManager(PIPES_TEST_CONFIG);
        LogAppender<Record> appender = manager.getAppender("test_images");
        LogLag lag = manager.getLag("test_images.out", "test_images.out>SaveEnrichmentFunction");
        assertEquals(0, lag.lag());
        LogOffset offset = appender.append("mykey", record);
        appender.waitFor(offset, "test_images>simpleTest>test_images.out", Duration.ofSeconds(10));
        assertEquals("We must have been called once", 1L, called.getValue());
        assertEquals("We must have produces one record", 1L, produced.getValue());
        lag = manager.getLag("test_images.out", "test_images.out>SaveEnrichmentFunction");
        assertEquals(0, lag.lag());
        txFeature.nextTransaction();
        DocumentModel enrichedDoc = session.getDocument(new IdRef(docId));
        assertTrue("The document must have the enrichment facet", enrichedDoc.hasFacet(ENRICHMENT_FACET));
        Property classProp = enrichedDoc.getPropertyObject(ENRICHMENT_NAME, ENRICHMENT_CLASSIFICATIONS);
        Assert.assertNotNull(classProp);
        assertEquals("simpleTest", classProp.get(0).get(AI_SERVICE_PROPERTY).getValue());
        Set<String> tags = tagService.getTags(session, docId);
        assertEquals(2, tags.size());


    }

}
