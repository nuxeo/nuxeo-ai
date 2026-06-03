/*
 * (C) Copyright 2026 Hyland (http://hyland.com/) and others.
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
 */
package org.nuxeo.ai.contentintelligence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.nuxeo.ai.enrichment.EnrichmentDescriptor;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.hyland.content.intelligence.http.ServiceCallResult;
import org.nuxeo.hyland.content.intelligence.service.enrichment.HylandKEService;

/**
 * Unit tests for {@link ContentIntelligenceEnrichmentProvider}. The HTTP surface is owned by the
 * upstream Content Intelligence connector; here we focus on the response-to-metadata mapping and
 * lifecycle around the {@link HylandKEService} call.
 */
@RunWith(MockitoJUnitRunner.class)
public class TestContentIntelligenceEnrichmentProvider {

    public static final String SUCCESS_RESPONSE = "{"
            + "\"responseCode\":200,"
            + "\"responseMessage\":\"OK\","
            + "\"objectKeysMapping\":[{\"sourceId\":\"s1\",\"objectKey\":\"ok1\"}],"
            + "\"response\":{"
            + "\"id\":\"job-1\","
            + "\"status\":\"SUCCESS\","
            + "\"results\":["
            + "{\"objectKey\":\"ok1\","
            + "\"imageDescription\":{\"isSuccess\":true,\"result\":\"A small test image\"},"
            + "\"namedEntityImage\":{\"isSuccess\":true,\"result\":"
            + "{\"locations\":[\"New York\",\"Paris\"],\"persons\":[\"Jane Doe\"]}},"
            + "\"imageEmbeddings\":{\"isSuccess\":true,\"result\":[0.1,0.2]}"
            + "}"
            + "]"
            + "}}";

    /**
     * Same wrapper but with the {@code text-*} actions returned by Hyland CI for non-image blobs (PDFs, DOCX, PPTX,
     * XLSX, plain text, ...). A failed action entry is also included so we assert that {@code isSuccess: false}
     * blocks are skipped.
     */
    public static final String TEXT_SUCCESS_RESPONSE = "{"
            + "\"responseCode\":200,"
            + "\"responseMessage\":\"OK\","
            + "\"objectKeysMapping\":[{\"sourceId\":\"s1\",\"objectKey\":\"ok1\"}],"
            + "\"response\":{"
            + "\"id\":\"job-text\","
            + "\"status\":\"SUCCESS\","
            + "\"results\":["
            + "{\"objectKey\":\"ok1\","
            + "\"textSummary\":{\"isSuccess\":true,\"result\":\"A sample contract document\"},"
            + "\"namedEntityText\":{\"isSuccess\":true,\"result\":"
            + "{\"organisations\":[\"Acme Corp\",\"Globex\"],\"persons\":[\"Jane Doe\"]}},"
            + "\"textEmbeddings\":{\"isSuccess\":true,\"result\":[0.11,0.42]},"
            + "\"textMetadataGeneration\":{\"isSuccess\":false,\"result\":null,\"error\":\"no KSimilarMetadata\"}"
            + "}"
            + "]"
            + "}}";

    /** Description-only entry: no taggable signal, but the description listener must still be triggered. */
    public static final String DESCRIPTION_ONLY_RESPONSE = "{"
            + "\"responseCode\":200,"
            + "\"responseMessage\":\"OK\","
            + "\"objectKeysMapping\":[],"
            + "\"response\":{"
            + "\"id\":\"job-d\",\"status\":\"SUCCESS\","
            + "\"results\":[{"
            + "\"objectKey\":\"ok1\","
            + "\"imageDescription\":{\"isSuccess\":true,\"result\":\"A picture of a sunset\"}"
            + "}]"
            + "}}";

    /** Empty entry: no description, no labels - we must not emit metadata for this entry. */
    public static final String NOTHING_USEFUL_RESPONSE = "{"
            + "\"responseCode\":200,"
            + "\"responseMessage\":\"OK\","
            + "\"objectKeysMapping\":[],"
            + "\"response\":{"
            + "\"id\":\"job-e\",\"status\":\"SUCCESS\","
            + "\"results\":[{"
            + "\"objectKey\":\"ok1\","
            + "\"imageEmbeddings\":{\"isSuccess\":true,\"result\":[0.1,0.2]}"
            + "}]"
            + "}}";

    /**
     * Mixed status: PARTIAL_FAILURE means some actions failed, but the surviving ones must still produce metadata.
     */
    public static final String PARTIAL_FAILURE_RESPONSE = "{"
            + "\"responseCode\":200,"
            + "\"responseMessage\":\"OK\","
            + "\"objectKeysMapping\":[],"
            + "\"response\":{"
            + "\"id\":\"job-pf\",\"status\":\"PARTIAL_FAILURE\","
            + "\"results\":[{"
            + "\"objectKey\":\"ok1\","
            + "\"imageDescription\":{\"isSuccess\":true,\"result\":\"A skyline\"},"
            + "\"namedEntityImage\":{\"isSuccess\":true,\"result\":{\"locations\":[\"skyline\"]}},"
            + "\"imageEmbeddings\":{\"isSuccess\":false,\"result\":null,\"error\":\"timeout\"}"
            + "}]"
            + "}}";

    /** PARTIAL_SUCCESS: same shape as SUCCESS, but Hyland CI flags the run as partial. */
    public static final String PARTIAL_SUCCESS_RESPONSE = "{"
            + "\"responseCode\":200,"
            + "\"responseMessage\":\"OK\","
            + "\"objectKeysMapping\":[],"
            + "\"response\":{"
            + "\"id\":\"job-ps\",\"status\":\"PARTIAL_SUCCESS\","
            + "\"results\":[{"
            + "\"objectKey\":\"ok1\","
            + "\"namedEntityImage\":{\"isSuccess\":true,\"result\":{\"locations\":[\"banner\"]}}"
            + "}]"
            + "}}";

    public static final String FAILURE_RESPONSE = "{"
            + "\"responseCode\":200,"
            + "\"responseMessage\":\"OK\","
            + "\"objectKeysMapping\":[],"
            + "\"response\":{\"id\":\"job-x\",\"status\":\"FAILURE\",\"results\":[]}"
            + "}";

    public static final String PROCESSING_RESPONSE = "{"
            + "\"responseCode\":200,"
            + "\"responseMessage\":\"OK\","
            + "\"objectKeysMapping\":[],"
            + "\"response\":{\"id\":\"job-p\",\"status\":\"PROCESSING\",\"results\":[]}"
            + "}";

    @Mock
    protected HylandKEService service;

    protected ContentIntelligenceEnrichmentProvider provider;

    protected AtomicInteger rawBlobsSaved;

    @Before
    public void setup() {
        rawBlobsSaved = new AtomicInteger();
        provider = new ContentIntelligenceEnrichmentProvider() {
            @Override
            protected HylandKEService getService() {
                return service;
            }

            @Override
            protected org.nuxeo.ecm.core.api.Blob resolveBlob(ManagedBlob managedBlob) {
                return org.nuxeo.ecm.core.api.Blobs.createBlob("stub-content");
            }

            @Override
            public String saveJsonAsRawBlob(String rawJson) {
                rawBlobsSaved.incrementAndGet();
                return "test-raw-blob-key";
            }
        };
        provider.init(buildDescriptor(Map.of(
                ContentIntelligenceEnrichmentProvider.OPTION_CONFIG_NAME, "default",
                ContentIntelligenceEnrichmentProvider.OPTION_ACTIONS,
                "image-description,image-embeddings,"
                        + "text-summarization,named-entity-recognition-text")));
    }

    @Test
    public void shouldMapSuccessfulResponseToLabelSuggestions() throws IOException {
        when(service.enrich(anyString(), any(org.nuxeo.ecm.core.api.Blob.class), anyList(), anyList(),
                nullable(String.class), nullable(String.class))).thenReturn(new ServiceCallResult(SUCCESS_RESPONSE));

        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc());
        assertNotNull(metadata);
        assertEquals(1, metadata.size());

        EnrichmentMetadata single = metadata.iterator().next();
        List<LabelSuggestion> suggestions = single.getLabels();

        Set<String> actions = suggestions.stream().map(LabelSuggestion::getProperty).collect(Collectors.toSet());
        assertEquals(Set.of("namedEntityImage"), actions);

        List<String> entities = valuesOf(suggestions, "namedEntityImage");
        assertEquals(3, entities.size());
        assertTrue(entities.contains("New York"));
        assertTrue(entities.contains("Paris"));
        assertTrue(entities.contains("Jane Doe"));

        // Description text MUST never be exposed as a label - even with the description action key as a prefix.
        assertTrue(suggestions.stream().noneMatch(s -> "imageDescription".equals(s.getProperty())));
        assertTrue(suggestions.stream()
                              .flatMap(s -> s.getValues().stream())
                              .noneMatch(l -> l.getName().contains("A small test image")));

        assertEquals("test-raw-blob-key", single.getRawKey());
        assertEquals("raw blob saved exactly once", 1, rawBlobsSaved.get());
    }

    @Test
    public void shouldEmitMetadataForDescriptionOnlyResponse() throws IOException {
        when(service.enrich(anyString(), any(org.nuxeo.ecm.core.api.Blob.class), anyList(), anyList(),
                nullable(String.class), nullable(String.class))).thenReturn(
                        new ServiceCallResult(DESCRIPTION_ONLY_RESPONSE));

        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc());
        // Even though no taggable suggestion exists, the description listener still needs an event with a rawKey,
        // otherwise dc:description will never be written.
        assertEquals(1, metadata.size());
        EnrichmentMetadata single = metadata.iterator().next();
        assertTrue("labels must be empty when only description is present", single.getLabels().isEmpty());
        assertEquals("test-raw-blob-key", single.getRawKey());
    }

    @Test
    public void shouldNotEmitMetadataWhenEntryHasNothingUseful() throws IOException {
        when(service.enrich(anyString(), any(org.nuxeo.ecm.core.api.Blob.class), anyList(), anyList(),
                nullable(String.class), nullable(String.class))).thenReturn(
                        new ServiceCallResult(NOTHING_USEFUL_RESPONSE));

        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc());
        assertEquals(0, metadata.size());
        assertEquals("raw blob must NOT be saved when nothing references it", 0, rawBlobsSaved.get());
    }

    @Test
    public void shouldHandlePartialFailureStatus() throws IOException {
        when(service.enrich(anyString(), any(org.nuxeo.ecm.core.api.Blob.class), anyList(), anyList(),
                nullable(String.class), nullable(String.class))).thenReturn(
                        new ServiceCallResult(PARTIAL_FAILURE_RESPONSE));

        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc());
        assertEquals(1, metadata.size());
        List<LabelSuggestion> suggestions = metadata.iterator().next().getLabels();
        Set<String> actions = suggestions.stream().map(LabelSuggestion::getProperty).collect(Collectors.toSet());
        assertEquals(Set.of("namedEntityImage"), actions);
        assertTrue("failed imageEmbeddings block must be dropped",
                suggestions.stream().noneMatch(s -> "imageEmbeddings".equals(s.getProperty())));
    }

    @Test
    public void shouldHandlePartialSuccessStatus() throws IOException {
        when(service.enrich(anyString(), any(org.nuxeo.ecm.core.api.Blob.class), anyList(), anyList(),
                nullable(String.class), nullable(String.class))).thenReturn(
                        new ServiceCallResult(PARTIAL_SUCCESS_RESPONSE));

        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc());
        assertEquals(1, metadata.size());
    }

    @Test
    public void shouldReturnEmptyOnFailureStatus() throws IOException {
        when(service.enrich(anyString(), any(org.nuxeo.ecm.core.api.Blob.class), anyList(), anyList(),
                nullable(String.class), nullable(String.class))).thenReturn(new ServiceCallResult(FAILURE_RESPONSE));

        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc());
        assertEquals(0, metadata.size());
    }

    @Test
    public void shouldReturnEmptyOn4xxResponse() throws IOException {
        // 4xx is a definitive client error: log and drop, do not retry.
        when(service.enrich(anyString(), any(org.nuxeo.ecm.core.api.Blob.class), anyList(), anyList(),
                nullable(String.class), nullable(String.class))).thenReturn(
                        new ServiceCallResult("{}", 400, "Bad Request"));

        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc());
        assertEquals(0, metadata.size());
    }

    @Test
    public void shouldThrowOn5xxResponseToTriggerRetry() throws IOException {
        // 5xx is a transient server failure: must throw so the upstream Failsafe retry policy actually engages.
        when(service.enrich(anyString(), any(org.nuxeo.ecm.core.api.Blob.class), anyList(), anyList(),
                nullable(String.class), nullable(String.class))).thenReturn(
                        new ServiceCallResult("{}", 503, "Service Unavailable"));
        try {
            provider.enrich(buildBlobTextFromDoc());
            fail("expected NuxeoException for 5xx response");
        } catch (NuxeoException expected) {
            assertTrue(expected.getMessage().contains("503"));
        }
    }

    @Test
    public void shouldThrowOn429ResponseToTriggerRetry() throws IOException {
        // Rate limiting should also trigger a retry instead of being silently dropped.
        when(service.enrich(anyString(), any(org.nuxeo.ecm.core.api.Blob.class), anyList(), anyList(),
                nullable(String.class), nullable(String.class))).thenReturn(
                        new ServiceCallResult("{}", 429, "Too Many Requests"));
        try {
            provider.enrich(buildBlobTextFromDoc());
            fail("expected NuxeoException for 429 response");
        } catch (NuxeoException expected) {
            assertTrue(expected.getMessage().contains("429"));
        }
    }

    @Test(expected = NuxeoException.class)
    public void shouldRethrowIOExceptionAsNuxeoException() throws IOException {
        when(service.enrich(anyString(), any(org.nuxeo.ecm.core.api.Blob.class), anyList(), anyList(),
                nullable(String.class), nullable(String.class))).thenThrow(new IOException("boom"));
        provider.enrich(buildBlobTextFromDoc());
    }

    @Test
    public void shouldReturnEmptyWhenNoBlob() {
        BlobTextFromDocument empty = new BlobTextFromDocument();
        empty.setRepositoryName("test");
        empty.setId("doc-1");
        Collection<EnrichmentMetadata> metadata = provider.enrich(empty);
        assertEquals(0, metadata.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRefuseConfigurationWithExplicitlyEmptyActions() {
        ContentIntelligenceEnrichmentProvider p = new ContentIntelligenceEnrichmentProvider();
        p.init(buildDescriptor(Map.of(ContentIntelligenceEnrichmentProvider.OPTION_ACTIONS, "")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRefuseConfigurationWithWhitespaceOnlyActions() {
        ContentIntelligenceEnrichmentProvider p = new ContentIntelligenceEnrichmentProvider();
        p.init(buildDescriptor(Map.of(ContentIntelligenceEnrichmentProvider.OPTION_ACTIONS, "   ,  ,")));
    }

    @Test
    public void shouldFallBackToDefaultActionsWhenOptionMissing() {
        ContentIntelligenceEnrichmentProvider p = new ContentIntelligenceEnrichmentProvider();
        p.init(buildDescriptor(Map.of()));
        assertEquals(List.of("image-description"), p.actions);
    }

    @Test
    public void shouldReturnMutableActionsList() {
        // splitCsv must produce a mutable list so downstream callers (or future code) cannot trip over an
        // UnsupportedOperationException.
        ContentIntelligenceEnrichmentProvider p = new ContentIntelligenceEnrichmentProvider();
        p.init(buildDescriptor(Map.of(ContentIntelligenceEnrichmentProvider.OPTION_ACTIONS, "a,b")));
        p.actions.add("c");
        assertEquals(List.of("a", "b", "c"), p.actions);
    }

    /**
     * Covers the document (PDF / DOCX / XLSX / PPTX / text) pipeline: verifies that {@code text-*} action results and
     * the {@code named-entity-recognition-text} entity buckets are flattened into the new per-action
     * {@link LabelSuggestion} shape.
     */
    @Test
    public void shouldMapTextSuccessResponseToLabelSuggestions() throws IOException {
        when(service.enrich(anyString(), any(org.nuxeo.ecm.core.api.Blob.class), anyList(), anyList(),
                nullable(String.class), nullable(String.class))).thenReturn(
                        new ServiceCallResult(TEXT_SUCCESS_RESPONSE));

        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc("application/pdf"));
        assertNotNull(metadata);
        assertEquals(1, metadata.size());

        List<LabelSuggestion> suggestions = metadata.iterator().next().getLabels();
        Set<String> actions = suggestions.stream().map(LabelSuggestion::getProperty).collect(Collectors.toSet());
        assertEquals(Set.of("namedEntityText"), actions);

        List<String> entities = valuesOf(suggestions, "namedEntityText");
        assertEquals(3, entities.size());
        assertTrue(entities.contains("Acme Corp"));
        assertTrue(entities.contains("Globex"));
        assertTrue(entities.contains("Jane Doe"));

        // Description text MUST never leak through as a label.
        assertTrue(suggestions.stream().noneMatch(s -> "textSummary".equals(s.getProperty())));
        assertTrue(suggestions.stream()
                              .flatMap(s -> s.getValues().stream())
                              .noneMatch(l -> l.getName().contains("A sample contract document")));

        // Failed action must never leak.
        assertTrue(suggestions.stream().noneMatch(s -> "textMetadataGeneration".equals(s.getProperty())));
    }

    @Test
    public void shouldReturnEmptyOnProcessingStatus() throws IOException {
        when(service.enrich(anyString(), any(org.nuxeo.ecm.core.api.Blob.class), anyList(), anyList(),
                nullable(String.class), nullable(String.class))).thenReturn(
                        new ServiceCallResult(PROCESSING_RESPONSE));

        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc("application/pdf"));
        assertEquals(0, metadata.size());
    }

    @Test
    public void shouldReturnEmptyWhenResponseIsNotJson() throws IOException {
        // Three-arg constructor short-circuits the wrapper parsing so we can feed a non-JSON body and exercise
        // the JSON parsing guard inside processResponse.
        when(service.enrich(anyString(), any(org.nuxeo.ecm.core.api.Blob.class), anyList(), anyList(),
                nullable(String.class), nullable(String.class))).thenReturn(
                        new ServiceCallResult("not-json-at-all", 200, "OK"));

        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc());
        assertEquals(0, metadata.size());
    }

    /**
     * The provider itself is MIME-type agnostic: the image pipe and the document pipe are separated at the stream
     * layer.
     */
    @Test
    public void shouldEnrichEveryDocumentMimeType() throws IOException {
        when(service.enrich(anyString(), any(org.nuxeo.ecm.core.api.Blob.class), anyList(), anyList(),
                nullable(String.class), nullable(String.class))).thenReturn(
                        new ServiceCallResult(TEXT_SUCCESS_RESPONSE));

        String[] mimeTypes = { "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "text/plain" };

        for (String mimeType : mimeTypes) {
            Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc(mimeType));
            assertEquals("expected enrichment for MIME type " + mimeType, 1, metadata.size());
            List<LabelSuggestion> suggestions = metadata.iterator().next().getLabels();
            assertTrue("textSummary must not leak as a label for " + mimeType,
                    suggestions.stream().noneMatch(s -> "textSummary".equals(s.getProperty())));
            assertTrue("expected namedEntityText for " + mimeType,
                    suggestions.stream().anyMatch(s -> "namedEntityText".equals(s.getProperty())));
        }
    }

    @Test
    public void shouldExposeStableDescriptionActionOrder() {
        // imageDescription must come first - this guards against Set.of() iteration randomization.
        List<String> ordered = List.copyOf(ContentIntelligenceConstants.DESCRIPTION_ACTION_KEYS);
        assertEquals(List.of("imageDescription", "textSummary"), ordered);
    }

    @Test
    public void shouldFlagOnly5xxAnd429AsRetryable() {
        ContentIntelligenceEnrichmentProvider p = new ContentIntelligenceEnrichmentProvider();
        assertTrue(p.isRetryable(-1));
        assertTrue(p.isRetryable(429));
        assertTrue(p.isRetryable(500));
        assertTrue(p.isRetryable(503));
        assertTrue(p.isRetryable(599));
        assertTrue("4xx must not be retryable", !p.isRetryable(400));
        assertTrue("4xx must not be retryable", !p.isRetryable(404));
        assertTrue("2xx must not be retryable", !p.isRetryable(200));
        assertTrue("3xx must not be retryable", !p.isRetryable(302));
        assertTrue("600+ must not be retryable", !p.isRetryable(600));
    }

    @Test
    public void shouldReturnCacheKeyBasedOnBlobDigests() {
        // Smoke-test the EnrichmentCachable contract; the underlying util builds a key from blob digests.
        BlobTextFromDocument doc = buildBlobTextFromDoc();
        String key = provider.getCacheKey(doc);
        assertNotNull(key);
    }

    /**
     * The provider must accept blobs of any size, including blobs well past the framework's default 5 MB cap and past
     * any historical {@code nuxeo.ai.contentintelligence.*maxSize} value, so that every eligible blob reaches the
     * Hyland CI API and CIC enforces its own ceiling instead of EnrichingStreamProcessor silently dropping the blob.
     */
    @Test
    public void shouldNotEnforceClientSideSizeCap() {
        assertTrue("0 bytes must be accepted", provider.supportsSize(0L));
        assertTrue("1 byte must be accepted", provider.supportsSize(1L));
        assertTrue("framework default (5 MB) must be accepted",
                provider.supportsSize(EnrichmentDescriptor.DEFAULT_MAX_SIZE));
        assertTrue("100 MB must be accepted", provider.supportsSize(100L * 1024 * 1024));
        assertTrue("10 GB must be accepted", provider.supportsSize(10L * 1024 * 1024 * 1024));
        assertTrue("Long.MAX_VALUE must be accepted", provider.supportsSize(Long.MAX_VALUE));
    }

    /** Returns every value carried by the suggestion whose {@code property} equals {@code action}. */
    protected static List<String> valuesOf(List<LabelSuggestion> suggestions, String action) {
        return suggestions.stream()
                          .filter(s -> action.equals(s.getProperty()))
                          .flatMap(s -> s.getValues().stream())
                          .map(AIMetadata.Label::getName)
                          .collect(Collectors.toList());
    }

    protected EnrichmentDescriptor buildDescriptor(Map<String, String> options) {
        EnrichmentDescriptor descriptor = new EnrichmentDescriptor();
        descriptor.name = "ai.contentintelligence.test";
        descriptor.kind = "/classification/imageLabels";
        descriptor.options = new HashMap<>(options);
        return descriptor;
    }

    /** Default builder: image blob, kept for backwards compatibility with the legacy image tests. */
    protected BlobTextFromDocument buildBlobTextFromDoc() {
        return buildBlobTextFromDoc("image/jpeg");
    }

    /**
     * Builds a {@link BlobTextFromDocument} with a single managed blob at {@code file:content} whose MIME type is
     * configurable.
     */
    protected BlobTextFromDocument buildBlobTextFromDoc(String mimeType) {
        BlobTextFromDocument doc = new BlobTextFromDocument();
        doc.setRepositoryName("test");
        doc.setId("doc-1");
        ManagedBlob blob = new BlobMetaImpl("test", mimeType, "test-key", "digest", null, 1024L);
        doc.addBlob("file:content", "img", blob);
        return doc;
    }
}
