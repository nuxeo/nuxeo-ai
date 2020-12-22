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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.ComputationMetadata;
import org.nuxeo.lib.stream.computation.ComputationMetadataMapping;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.internals.ComputationContextImpl;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import javax.inject.Inject;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.nuxeo.ai.enrichment.EnrichmentTestFeature.FILE_CONTENT;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toRecord;
import static org.nuxeo.ai.services.AIComponent.ENRICHMENT_XP;

/**
 * Tests the overall AIComponent
 */
@RunWith(FeaturesRunner.class)
@Features({ EnrichmentTestFeature.class, PlatformFeature.class })
@Deploy({ "org.nuxeo.ecm.platform.tag", "org.nuxeo.ai.ai-core:OSGI-INF/enrichment-test.xml" })
public class TestAIComponent {

    @Inject
    protected AIComponent aiComponent;

    @Test
    public void testBasicComponent() {
        assertNotNull(aiComponent);
        assertEquals(5, aiComponent.getEnrichmentProviders().size());
        EnrichmentProvider service = aiComponent.getEnrichmentProvider("test.e1");
        assertEquals("test.e1", service.getName());

        ComputationContext testContext = new ComputationContextImpl(null);
        BlobTextFromDocument blobTextFromDoc = new BlobTextFromDocument();
        blobTextFromDoc.addBlob(FILE_CONTENT, "img",
                new BlobMetaImpl("test", "application/pdf", "xyx", "xyz", null, 45L));
        Record record = toRecord("k", blobTextFromDoc);

        EnrichingStreamProcessor.EnrichmentMetrics metrics = new EnrichingStreamProcessor.EnrichmentMetrics("test.e1");
        EnrichingStreamProcessor.EnrichmentComputation computation = new EnrichingStreamProcessor.EnrichmentComputation(
                1, "test", "test.e1", metrics, false);
        computation.init(testContext);
        computation.processRecord(testContext, null, record);
        assertEquals(0, metrics.errors);
        assertEquals(0, metrics.called);
        assertEquals(0, metrics.success);
        assertEquals("PDF isn't a supported mimetype", 1, metrics.unsupported);

        service = aiComponent.getEnrichmentProvider("test.logging");
        service.enrich(blobTextFromDoc);
    }

    @Test
    @Deploy({ "org.nuxeo.ai.ai-core:OSGI-INF/erroring-enrichment-test.xml" })
    public void testEnrichingStreamProcessor() {

        ComputationContext testContext = setupComputationContext();
        Record record = setupTestRecord();

        EnrichingStreamProcessor.EnrichmentMetrics metrics = new EnrichingStreamProcessor.EnrichmentMetrics("test");
        EnrichingStreamProcessor.EnrichmentComputation computation = new EnrichingStreamProcessor.EnrichmentComputation(
                1, "test", "error1", metrics, false);
        computation.init(testContext);
        computation.processRecord(testContext, null, record);

        assertEquals(3, metrics.retries);
        assertEquals(1, metrics.called);
        assertEquals(1, metrics.success);

        metrics = new EnrichingStreamProcessor.EnrichmentMetrics("test2");
        computation = new EnrichingStreamProcessor.EnrichmentComputation(1, "test2", "error2", metrics, false);
        computation.init(testContext);
        computation.processRecord(testContext, null, record);

        assertEquals(0, metrics.retries);
        assertEquals(1, metrics.called);
        assertEquals(1, metrics.success);

        metrics = new EnrichingStreamProcessor.EnrichmentMetrics("test3");
        computation = new EnrichingStreamProcessor.EnrichmentComputation(1, "test3", "error3", metrics, false);
        computation.init(testContext);
        computation.processRecord(testContext, null, record);
        assertEquals(0, metrics.retries);
        assertEquals(1, metrics.called);
        assertEquals(0, metrics.success);
        assertEquals(1, metrics.errors);

        metrics = new EnrichingStreamProcessor.EnrichmentMetrics("test4");
        computation = new EnrichingStreamProcessor.EnrichmentComputation(1, "test4", "error4", metrics, false);
        computation.init(testContext);
        computation.processRecord(testContext, null, record);

        assertEquals(1, metrics.retries);
        assertEquals(1, metrics.errors);
        assertEquals(1, metrics.called);
        assertEquals(1, metrics.success);
    }

    @Test
    @Deploy({ "org.nuxeo.ai.ai-core:OSGI-INF/erroring-enrichment-test.xml" })
    public void testCircuitBreakerAndRetries() {

        ComputationContext testContext = setupComputationContext();
        Record record = setupTestRecord();

        EnrichingStreamProcessor.EnrichmentMetrics metrics = new EnrichingStreamProcessor.EnrichmentMetrics(
                "testErrors");
        EnrichingStreamProcessor.EnrichmentComputation computation = new EnrichingStreamProcessor.EnrichmentComputation(
                1, "teste1", "circ1", metrics, false);
        computation.init(testContext);
        computation.processRecord(testContext, null, record);
        computation.processRecord(testContext, null, record);
        computation.processRecord(testContext, null, record);
        computation.processRecord(testContext, null, record);
        computation.processRecord(testContext, null, record);
        computation.processRecord(testContext, null, record);

        assertEquals(1, metrics.retries);
        assertEquals(2, metrics.errors);
        assertEquals(6, metrics.called);
        assertEquals(5, metrics.success);
        assertEquals(0, metrics.circuitBreaker);
        assertEquals(0, metrics.fatal);

        metrics = new EnrichingStreamProcessor.EnrichmentMetrics("testCirc");
        computation = new EnrichingStreamProcessor.EnrichmentComputation(1, "teste2", "circ2", metrics, false);
        computation.init(testContext);
        computation.processRecord(testContext, null, record);
        computation.processRecord(testContext, null, record);
        computation.processRecord(testContext, null, record);
        try {
            computation.processRecord(testContext, null, record);
            fail();
        } catch (NuxeoException e) {
            assertTrue(e.getMessage().contains("Stream circuit breaker"));
            assertEquals(1, metrics.circuitBreaker);
        }
        assertEquals(6, metrics.retries);
        assertEquals(8, metrics.errors);
        assertEquals(4, metrics.called);
        assertEquals(1, metrics.success);

        metrics = new EnrichingStreamProcessor.EnrichmentMetrics("testError");
        computation = new EnrichingStreamProcessor.EnrichmentComputation(1, "teste3", "circ3", metrics, false);
        computation.init(testContext);
        assertEquals(0, metrics.fatal);
        try {
            computation.processRecord(testContext, null, record);
            fail();
        } catch (NuxeoException e) {
            assertEquals("FatalEnrichmentError", e.getClass().getSimpleName());
        }
        assertEquals(0, metrics.retries);
        assertEquals(1, metrics.errors);
        assertEquals(1, metrics.called);
        assertEquals(0, metrics.success);
        assertEquals(1, metrics.fatal);
    }

    protected Record setupTestRecord() {
        BlobTextFromDocument blobTextFromDoc = new BlobTextFromDocument();
        blobTextFromDoc.setId("xderftgt");
        blobTextFromDoc.setRepositoryName("test");
        blobTextFromDoc.addBlob(FILE_CONTENT, "img",
                new BlobMetaImpl("test", "application/pdf", "xyx", "xyz", null, 45L));
        return toRecord("k", blobTextFromDoc);
    }

    protected ComputationContext setupComputationContext() {
        ComputationMetadataMapping meta = new ComputationMetadataMapping(
                new ComputationMetadata("myName", singleton("i1"), singleton("o1")), emptyMap());
        return new ComputationContextImpl(meta);
    }

    @Test
    @Deploy({ "org.nuxeo.ai.ai-core:OSGI-INF/bad-enrichment-test.xml" })
    public void testBadConfig() {
        assertNotNull(aiComponent);
        try {
            aiComponent.getEnrichmentProvider("b1");
            fail();
        } catch (NuxeoException e) {
            assertTrue(e.getMessage().contains("must define a valid EnrichmentProvider"));
        }

        EnrichmentProvider service = aiComponent.getEnrichmentProvider("ok1");
        assertNotNull(service);

        EnrichmentDescriptor descriptor = new EnrichmentDescriptor();
        String badProvider = "bad provider";
        descriptor.name = badProvider;
        aiComponent.registerContribution(descriptor, ENRICHMENT_XP, null);

        try {
            aiComponent.getEnrichmentProvider(badProvider);
            fail();
        } catch (NuxeoException e) {
            assertTrue(e.getMessage().contains("must define a valid EnrichmentProvider"));
        }

        descriptor.service = BasicEnrichmentProvider.class;
        try {
            aiComponent.getEnrichmentProvider(badProvider);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("provider must be configured with a name bad provider and kind null"));
        }

        descriptor.kind = "/NOT_ME";
        try {
            aiComponent.getEnrichmentProvider(badProvider);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(
                    e.getMessage()
                     .contains("The /NOT_ME kind for provider bad provider must be defined in the aikind vocabulary"));
        }

        descriptor.kind = "/classification/sentiment";
        service = aiComponent.getEnrichmentProvider(badProvider);
        assertNotNull(service);

        assertNull(aiComponent.getEnrichmentProvider("IDONTEXIST"));

        EnrichingStreamProcessor.EnrichmentMetrics metrics = new EnrichingStreamProcessor.EnrichmentMetrics(
                "badMetrics");
        EnrichingStreamProcessor.EnrichmentComputation computation = new EnrichingStreamProcessor.EnrichmentComputation(
                1, "b1", "IDONTEXIST", metrics, false);
        try {
            computation.init(null);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unknown enrichment provider IDONTEXIST"));
        }

    }

}
