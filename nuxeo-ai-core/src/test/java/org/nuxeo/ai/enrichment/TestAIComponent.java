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


import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_XP;
import static org.nuxeo.ai.enrichment.EnrichmentTestFeature.PIPES_TEST_CONFIG;
import static org.nuxeo.ai.AIConstants.AI_SERVICE_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_CLASSIFICATIONS;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_NAME;
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.toRecord;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.AIComponent;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.platform.tag.TagService;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.ComputationMetadata;
import org.nuxeo.lib.stream.computation.ComputationMetadataMapping;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.internals.ComputationContextImpl;
import org.nuxeo.lib.stream.log.LogAppender;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.lib.stream.log.LogOffset;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.metrics.MetricsService;
import org.nuxeo.runtime.stream.StreamService;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.transaction.TransactionHelper;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.inject.Inject;

/**
 * Tests the overall AIComponent
 */
@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, PlatformFeature.class})
@Deploy({"org.nuxeo.ecm.platform.tag", "org.nuxeo.ai.ai-core:OSGI-INF/enrichment-test.xml"})
public class TestAIComponent {

    @Inject
    protected AIComponent aiComponent;

    @Inject
    CoreSession session;

    @Inject
    TagService tagService;

    @Test
    public void TestBasicComponent() {
        assertNotNull(aiComponent);
        assertEquals(5, aiComponent.getEnrichmentServices().size());
        EnrichmentService service = aiComponent.getEnrichmentService("e1");
        assertEquals("e1", service.getName());

        ComputationContext testContext = new ComputationContextImpl(null);
        BlobTextStream blobTextStream = new BlobTextStream();
        blobTextStream.setBlob(new BlobMetaImpl("test", "application/pdf", "xyx", "xyz", null, 45L));
        Record record = toRecord("k", blobTextStream);

        EnrichingStreamProcessor.EnrichmentComputation computation
                = new EnrichingStreamProcessor.EnrichmentComputation(1, "test", service);

        computation.processRecord(testContext, null, record);
        assertEquals(0, computation.errors);
        assertEquals(0, computation.called);
        assertEquals(0, computation.success);
        assertEquals("PDF isn't a supported mimetype", 1, computation.unsupported);

        service = aiComponent.getEnrichmentService("logging");
        service.enrich(blobTextStream);
    }

    @Test
    public void TestEnrichingStreamProcessor() {

        ComputationMetadataMapping meta = new ComputationMetadataMapping(
                new ComputationMetadata("myName",
                                        IntStream
                                                .range(1, 1 + 1)
                                                .boxed()
                                                .map(i -> "i" + i)
                                                .collect(Collectors.toSet()),
                                        IntStream
                                                .range(1, 1 + 1)
                                                .boxed()
                                                .map(i -> "o" + i)
                                                .collect(Collectors.toSet())
                ), Collections.emptyMap());
        ComputationContext testContext = new ComputationContextImpl(meta);
        BlobTextStream blobTextStream = new BlobTextStream();
        blobTextStream.setBlob(new BlobMetaImpl("test", "application/pdf", "xyx", "xyz", null, 45L));
        Record record = toRecord("k", blobTextStream);

        EnrichingStreamProcessor.EnrichmentComputation computation = new EnrichingStreamProcessor.EnrichmentComputation(1, "test",
                                                                                                                        new ErroringEnrichmentService(new NoSuchElementException(), 3, 3)
        );
        computation.init(testContext);
        computation.processRecord(testContext, null, record);

        assertEquals(3, computation.retries);
        assertEquals(1, computation.called);
        assertEquals(1, computation.success);

        computation = new EnrichingStreamProcessor.EnrichmentComputation(1, "test2",
                                                                         new ErroringEnrichmentService(new NoSuchElementException(), 0, 0)
        );
        computation.init(testContext);
        computation.processRecord(testContext, null, record);

        assertEquals(0, computation.retries);
        assertEquals(1, computation.called);
        assertEquals(1, computation.success);

        computation = new EnrichingStreamProcessor.EnrichmentComputation(1, "test3",
                                                                         new ErroringEnrichmentService(new NoSuchElementException(), 1, 0)
        );
        computation.init(testContext);
        try {
            computation.processRecord(testContext, null, record);
            fail(); // Should not get here
        } catch (NuxeoException e) {
            assertEquals(NoSuchElementException.class, e.getCause().getClass());
        }

        assertEquals(0, computation.retries);
        assertEquals(1, computation.called);
        assertEquals(0, computation.success);
        assertEquals(1, computation.errors);

        computation = new EnrichingStreamProcessor.EnrichmentComputation(1, "test4",
                                                                         new ErroringEnrichmentService(new NoSuchElementException(), 1, 1)
        );
        computation.init(testContext);
        computation.processRecord(testContext, null, record);

        assertEquals(1, computation.retries);
        assertEquals(1, computation.errors);
        assertEquals(1, computation.called);
        assertEquals(1, computation.success);
    }

    @Test
    @Deploy({"org.nuxeo.ai.ai-core:OSGI-INF/stream-test.xml"})
    public void TestConfiguredStreamProcessor() throws Exception {

        DocumentModel testDoc = session.createDocumentModel("/", "My Doc", "File");
        testDoc = session.createDocument(testDoc);
        final String docId = testDoc.getId();
        session.save();
        nextTransaction();
        BlobTextStream blobTextStream = new BlobTextStream();
        blobTextStream.setId(docId);
        blobTextStream.setRepositoryName(testDoc.getRepositoryName());
        blobTextStream.setBlob(new BlobMetaImpl("test", "image/jpeg", "xyx", "xyz", null, 45L));
        Record record = toRecord("k", blobTextStream);
        String metricPrefix = "nuxeo.streams.enrichment.images>simpleTest>images.out.";
        MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());
        Map<String, Gauge> gauges = registry.getGauges().entrySet().stream()
                                            .filter(e -> e.getKey().startsWith(metricPrefix))
                                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Gauge called = gauges.get(metricPrefix + "called");
        Gauge produced = gauges.get(metricPrefix + "produced");
        assertEquals(0L, called.getValue());
        assertEquals(0L, produced.getValue());
        LogManager manager = Framework.getService(StreamService.class).getLogManager(PIPES_TEST_CONFIG);
        LogAppender<Record> appender = manager.getAppender("images");
        LogOffset offset = appender.append("mykey", record);
        appender.waitFor(offset, "images.out>StoreLabelsAsTags", Duration.ofSeconds(5));
        TransactionHelper.runInTransaction(() -> {
            DocumentModel enrichedDoc = session.getDocument(new IdRef(docId));
            assertTrue(enrichedDoc.hasFacet(ENRICHMENT_FACET));
            Property classProp = enrichedDoc.getPropertyObject(ENRICHMENT_NAME, ENRICHMENT_CLASSIFICATIONS);
            Assert.assertNotNull(classProp);
            assertEquals("simpleTest", classProp.get(0).get(AI_SERVICE_PROPERTY).getValue());
            Set<String> tags = tagService.getTags(session, docId);
            assertEquals(2, tags.size());
        });

        assertEquals(1L, called.getValue());
        assertEquals(1L, produced.getValue());


    }

    @Test
    @Deploy({"org.nuxeo.ai.ai-core:OSGI-INF/bad-enrichment-test.xml"})
    public void TestBadConfig() {
        assertNotNull(aiComponent);
        try {
            aiComponent.getEnrichmentService("b1");
            fail();
        } catch (NuxeoException e) {
            assertTrue(e.getMessage().contains("must define a valid EnrichmentService"));
        }

        EnrichmentService service = aiComponent.getEnrichmentService("ok1");
        assertNotNull(service);

        EnrichmentDescriptor descriptor = new EnrichmentDescriptor();
        String badService = "bad service";
        descriptor.name = badService;
        aiComponent.registerContribution(descriptor, ENRICHMENT_XP, null);

        try {
            aiComponent.getEnrichmentService(badService);
            fail();
        } catch (NuxeoException e) {
            assertTrue(e.getMessage().contains("must define a valid EnrichmentService"));
        }

        descriptor.service = BasicEnrichmentService.class;
        try {
            aiComponent.getEnrichmentService(badService);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("service must be configured with a name bad service and kind null"));
        }

        descriptor.kind = "/NOT_ME";
        try {
            aiComponent.getEnrichmentService(badService);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("The /NOT_ME kind must be defined in the aikind vocabulary"));
        }

        descriptor.kind = "/classification/sentiment";
        service = aiComponent.getEnrichmentService(badService);
        assertNotNull(service);

        assertNull(aiComponent.getEnrichmentService("IDONTEXIST"));
    }

    protected void nextTransaction() {
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
    }
}
