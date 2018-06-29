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
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.toRecord;

import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.ComputationMetadata;
import org.nuxeo.lib.stream.computation.ComputationMetadataMapping;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.internals.ComputationContextImpl;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

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

        EnrichingStreamProcessor.EnrichmentMetrics metrics = new EnrichingStreamProcessor.EnrichmentMetrics("e1");
        EnrichingStreamProcessor.EnrichmentComputation computation
                = new EnrichingStreamProcessor.EnrichmentComputation(1, "test", service, metrics);

        computation.processRecord(testContext, null, record);
        assertEquals(0, metrics.errors);
        assertEquals(0, metrics.called);
        assertEquals(0, metrics.success);
        assertEquals("PDF isn't a supported mimetype", 1, metrics.unsupported);

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
        blobTextStream.setId("xderftgt");
        blobTextStream.setRepositoryName("test");
        blobTextStream.setBlob(new BlobMetaImpl("test", "application/pdf", "xyx", "xyz", null, 45L));
        Record record = toRecord("k", blobTextStream);


        EnrichingStreamProcessor.EnrichmentMetrics metrics = new EnrichingStreamProcessor.EnrichmentMetrics("test");
        EnrichingStreamProcessor.EnrichmentComputation computation =
                new EnrichingStreamProcessor.EnrichmentComputation(1, "test",
                                                                   new ErroringEnrichmentService(new NoSuchElementException(), 3, 3),
                                                                   metrics);
        computation.init(testContext);
        computation.processRecord(testContext, null, record);

        assertEquals(3, metrics.retries);
        assertEquals(1, metrics.called);
        assertEquals(1, metrics.success);

        metrics = new EnrichingStreamProcessor.EnrichmentMetrics("test2");
        computation = new EnrichingStreamProcessor.EnrichmentComputation(1, "test2",
                                                                         new ErroringEnrichmentService(new NoSuchElementException(), 0, 0),
                                                                         metrics);
        computation.init(testContext);
        computation.processRecord(testContext, null, record);

        assertEquals(0, metrics.retries);
        assertEquals(1, metrics.called);
        assertEquals(1, metrics.success);

        metrics = new EnrichingStreamProcessor.EnrichmentMetrics("test3");
        computation = new EnrichingStreamProcessor.EnrichmentComputation(1, "test3",
                                                                         new ErroringEnrichmentService(new NoSuchElementException(), 1, 0),
                                                                         metrics);
        computation.init(testContext);
        try {
            computation.processRecord(testContext, null, record);
            fail(); // Should not get here
        } catch (NuxeoException e) {
            assertEquals(NoSuchElementException.class, e.getCause().getClass());
        }

        assertEquals(0, metrics.retries);
        assertEquals(1, metrics.called);
        assertEquals(0, metrics.success);
        assertEquals(1, metrics.errors);

        metrics = new EnrichingStreamProcessor.EnrichmentMetrics("test4");
        computation = new EnrichingStreamProcessor.EnrichmentComputation(1, "test4",
                                                                         new ErroringEnrichmentService(new NoSuchElementException(), 1, 1),
                                                                         metrics);
        computation.init(testContext);
        computation.processRecord(testContext, null, record);

        assertEquals(1, metrics.retries);
        assertEquals(1, metrics.errors);
        assertEquals(1, metrics.called);
        assertEquals(1, metrics.success);
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
            assertTrue(e.getMessage()
                        .contains("The /NOT_ME kind for service bad service must be defined in the aikind vocabulary"));
        }

        descriptor.kind = "/classification/sentiment";
        service = aiComponent.getEnrichmentService(badService);
        assertNotNull(service);

        assertNull(aiComponent.getEnrichmentService("IDONTEXIST"));
    }

}
