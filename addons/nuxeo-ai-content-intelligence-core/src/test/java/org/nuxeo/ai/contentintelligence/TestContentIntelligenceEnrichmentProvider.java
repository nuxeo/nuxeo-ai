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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

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
import org.nuxeo.ecm.core.api.Blob;
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

    /**
     * Mirrors the production failure shape: terminal FAILURE with every requested action carrying an
     * {@code error} block. Used by the cleanup tests to verify the WARN summary is structured rather than a raw
     * JSON dump.
     */
    public static final String FAILURE_RESPONSE_WITH_ACTION_ERRORS = "{"
            + "\"responseCode\":200,"
            + "\"responseMessage\":\"OK\","
            + "\"objectKeysMapping\":[],"
            + "\"response\":{"
            + "\"id\":\"f2f942f5-e7cb-4566\",\"status\":\"FAILURE\","
            + "\"results\":[{"
            + "\"objectKey\":\"ok1\","
            + "\"textSummary\":{\"isSuccess\":false,\"result\":null,"
            + "\"error\":{\"errorType\":\"UnexpectedError\","
            + "\"message\":\"An error occured while processing the request\"}},"
            + "\"namedEntityText\":{\"isSuccess\":false,\"result\":null,"
            + "\"error\":{\"errorType\":\"UnexpectedError\","
            + "\"message\":\"An error occured while processing the request\"}}"
            + "}]"
            + "}}";

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

    /**
     * A FAILURE response that carries per-action {@code error} blocks (the shape Hyland CI actually returns for
     * server-side issues) must still drop cleanly: empty metadata, no exception, and no retry storm. The richer
     * WARN content is observed via {@link #shouldSummarizeActionErrorsFromFailureResponse()}.
     */
    @Test
    public void shouldReturnEmptyOnFailureStatusWithActionErrors() throws IOException {
        when(service.enrich(anyString(), any(org.nuxeo.ecm.core.api.Blob.class), anyList(), anyList(),
                nullable(String.class), nullable(String.class))).thenReturn(
                        new ServiceCallResult(FAILURE_RESPONSE_WITH_ACTION_ERRORS));

        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc());
        assertEquals(0, metadata.size());
    }

    /**
     * The per-action error summary must list the action name and the {@code errorType: message} pair so an operator
     * can triage CIC server-side failures without grepping the full JSON dump.
     */
    @Test
    public void shouldSummarizeActionErrorsFromFailureResponse() {
        org.json.JSONObject body = new org.json.JSONObject(FAILURE_RESPONSE_WITH_ACTION_ERRORS);
        org.json.JSONObject response = body.getJSONObject("response");

        String summary = provider.summarizeActionErrors(response);

        assertTrue("summary should call out textSummary: " + summary,
                summary.contains("textSummary[UnexpectedError: An error occured while processing the request]"));
        assertTrue("summary should call out namedEntityText: " + summary,
                summary.contains("namedEntityText[UnexpectedError: An error occured while processing the request]"));
    }

    /**
     * Responses without per-action errors (e.g. PROCESSING with empty results) must yield an empty summary so the
     * caller can fall back to a status-only WARN instead of an awkward {@code Action errors: } trailer.
     */
    @Test
    public void shouldReturnEmptySummaryWhenNoActionErrorsPresent() {
        org.json.JSONObject body = new org.json.JSONObject(PROCESSING_RESPONSE);
        org.json.JSONObject response = body.getJSONObject("response");

        assertEquals("", provider.summarizeActionErrors(response));
    }

    /**
     * Wide actions lists must not produce 1 KB+ WARN lines: the summary truncates at
     * {@link ContentIntelligenceEnrichmentProvider#MAX_ERRORS_IN_SUMMARY} entries and appends a {@code ...}
     * marker so the reader knows there are more.
     */
    @Test
    public void shouldTruncateActionErrorSummary() {
        StringBuilder json = new StringBuilder("{\"id\":\"job-many\",\"status\":\"FAILURE\",\"results\":[{");
        json.append("\"objectKey\":\"ok1\"");
        int actionCount = ContentIntelligenceEnrichmentProvider.MAX_ERRORS_IN_SUMMARY + 3;
        for (int i = 0; i < actionCount; i++) {
            json.append(",\"action").append(i)
                .append("\":{\"isSuccess\":false,\"error\":{\"errorType\":\"E\",\"message\":\"m\"}}");
        }
        json.append("}]}");
        org.json.JSONObject response = new org.json.JSONObject(json.toString());

        String summary = provider.summarizeActionErrors(response);

        assertTrue("summary should end with ellipsis when truncated: " + summary, summary.endsWith("..."));
        long entries = summary.chars().filter(c -> c == ',').count();
        // MAX_ERRORS_IN_SUMMARY entries + "..." separator = MAX commas
        assertEquals(ContentIntelligenceEnrichmentProvider.MAX_ERRORS_IN_SUMMARY, entries);
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
     * The framework-level {@code supportsSize} pre-filter must remain permissive so every eligible blob reaches the
     * provider: the real image-byte budget is enforced by {@code transcodeIfNeeded} via re-encoding / downscaling,
     * not by short-circuiting in {@code EnrichingStreamProcessor}. Document blobs have no client-side cap at all
     * (CIC text-* actions accept much larger payloads than image-* actions do).
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

    /**
     * Hyland CI's image-* actions reject blobs over 5 MB with {@code ValidationError: Image size exceeds 5 MB}.
     * When the source bytes are already over the budget the provider must JPEG-re-encode (and if needed downscale)
     * the image so it slips under the cap, instead of round-tripping the original to a guaranteed-rejection call.
     * <p>
     * Uses a 1024x1024 random-noise fixture (worst-case for JPEG compression) so the test cannot accidentally
     * pass by relying on a degenerate compressible input. Budget is sized for the downscale ladder to land at
     * 256x256 (the {@code MIN_DOWNSCALE_DIMENSION} floor) at the lowest quality step.
     */
    @Test
    public void shouldReencodeOversizedImageWithinByteBudget() throws IOException {
        ContentIntelligenceEnrichmentProvider p = new ContentIntelligenceEnrichmentProvider();
        long budget = 32 * 1024L; // 32 KB - achievable for a 256x256 q=0.4 noisy JPEG
        p.setImageMaxBytes(budget);
        Blob oversized = buildNoisyImageBlob(1024, "jpg", "image/jpeg", "huge.jpg");
        assertTrue("fixture must exceed test budget to exercise the encoder",
                oversized.getLength() > budget);

        Blob fitted = p.transcodeIfNeeded(oversized);

        assertNotNull("oversize but compressible images must produce a fitted payload, not be skipped", fitted);
        assertNotSame("oversized blob must be re-encoded, not passed through as-is", oversized, fitted);
        assertEquals(ContentIntelligenceEnrichmentProvider.TRANSCODE_TARGET_MIME_TYPE, fitted.getMimeType());
        assertTrue("re-encoded payload must fit within the configured byte budget (got " + fitted.getLength() + ")",
                fitted.getLength() <= budget);
    }

    /**
     * If even maximum downscale + minimum JPEG quality cannot make the image fit, the provider must signal "skip"
     * (return {@code null}) instead of sending a doomed call to CIC.
     */
    @Test
    public void shouldReturnNullWhenImageCannotFitByteBudget() throws IOException {
        ContentIntelligenceEnrichmentProvider p = new ContentIntelligenceEnrichmentProvider();
        // 1-byte budget: not even a minimal JPEG header fits.
        p.setImageMaxBytes(1L);
        Blob oversized = buildNoisyImageBlob(64, "jpg", "image/jpeg", "uncompressible.jpg");

        Blob result = p.transcodeIfNeeded(oversized);

        assertEquals("unsatisfiable budget must yield null so enrich() can skip the blob", null, result);
    }

    /**
     * Natively-supported, under-cap blobs must short-circuit (no re-encode, no allocation) — that path is the hot
     * loop and any regression here means we'd waste CPU on every well-formed JPEG / PNG / TIFF upload.
     */
    @Test
    public void shouldShortCircuitForNativeUnderCapBlobs() throws IOException {
        ContentIntelligenceEnrichmentProvider p = new ContentIntelligenceEnrichmentProvider();
        Blob tiny = buildSyntheticImageBlob("jpg", "image/jpeg", "tiny.jpg");
        assertTrue("fixture must be well under the 5 MB cap",
                tiny.getLength() < ContentIntelligenceEnrichmentProvider.CIC_IMAGE_MAX_BYTES);

        assertSame("native + under-cap blob must pass through unchanged", tiny, p.transcodeIfNeeded(tiny));
    }

    /**
     * End-to-end: an oversize image is downscaled, sent to CIC as a JPEG within the byte budget, and the resulting
     * enrichment metadata is returned normally - the caller never sees the resize happen.
     */
    @Test
    public void enrichShouldSendDownscaledBlobForOversizedImageInput() throws IOException {
        when(service.enrich(anyString(), any(Blob.class), anyList(), anyList(), nullable(String.class),
                nullable(String.class))).thenReturn(new ServiceCallResult(SUCCESS_RESPONSE));

        ContentIntelligenceEnrichmentProvider bigProvider = new ContentIntelligenceEnrichmentProvider() {
            @Override
            protected HylandKEService getService() {
                return service;
            }

            @Override
            protected Blob resolveBlob(ManagedBlob managedBlob) {
                try {
                    return buildNoisyImageBlob(1024, "jpg", "image/jpeg", "huge.jpg");
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            }

            @Override
            public String saveJsonAsRawBlob(String rawJson) {
                return "test-raw-blob-key";
            }
        };
        bigProvider.init(buildDescriptor(Map.of(
                ContentIntelligenceEnrichmentProvider.OPTION_CONFIG_NAME, "default",
                ContentIntelligenceEnrichmentProvider.OPTION_ACTIONS,
                "image-description,named-entity-recognition-image")));
        long budget = 32 * 1024L;
        bigProvider.setImageMaxBytes(budget);

        BlobTextFromDocument doc = buildBlobTextFromDoc("image/jpeg");
        Collection<EnrichmentMetadata> metadata = bigProvider.enrich(doc);
        assertEquals(1, metadata.size());

        org.mockito.ArgumentCaptor<Blob> sent = org.mockito.ArgumentCaptor.forClass(Blob.class);
        org.mockito.Mockito.verify(service)
                           .enrich(anyString(), sent.capture(), anyList(), anyList(), nullable(String.class),
                                   nullable(String.class));
        assertTrue("oversized image must reach CIC under the byte budget (sent " + sent.getValue().getLength() + ")",
                sent.getValue().getLength() <= budget);
        assertEquals("re-encoded payload must be JPEG", ContentIntelligenceEnrichmentProvider.TRANSCODE_TARGET_MIME_TYPE,
                sent.getValue().getMimeType());
    }

    /**
     * When the blob is unfit even after maximum downscale, the provider must NOT call CIC at all — wasted API
     * calls are not free, and CIC just rejects with a deterministic ValidationError.
     */
    @Test
    public void enrichShouldSkipBlobThatCannotFitByteBudget() throws IOException {
        ContentIntelligenceEnrichmentProvider bigProvider = new ContentIntelligenceEnrichmentProvider() {
            @Override
            protected HylandKEService getService() {
                return service;
            }

            @Override
            protected Blob resolveBlob(ManagedBlob managedBlob) {
                try {
                    return buildNoisyImageBlob(64, "jpg", "image/jpeg", "huge.jpg");
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            }
        };
        bigProvider.init(buildDescriptor(Map.of(
                ContentIntelligenceEnrichmentProvider.OPTION_CONFIG_NAME, "default",
                ContentIntelligenceEnrichmentProvider.OPTION_ACTIONS,
                "image-description,named-entity-recognition-image")));
        bigProvider.setImageMaxBytes(1L);

        Collection<EnrichmentMetadata> metadata = bigProvider.enrich(buildBlobTextFromDoc("image/jpeg"));
        assertEquals("doomed blobs must be skipped without producing metadata", 0, metadata.size());

        org.mockito.Mockito.verify(service, org.mockito.Mockito.never())
                           .enrich(anyString(), any(Blob.class), anyList(), anyList(), nullable(String.class),
                                   nullable(String.class));
    }

    /**
     * Hyland CI's image-* actions only support JPEG/PNG/TIFF natively. The provider must transcode any other
     * {@code image/*} blob (here BMP) to JPEG before handing it to the service, otherwise the API rejects it and
     * users get no enrichment at all.
     */
    @Test
    public void shouldTranscodeBmpToJpegBeforeSending() throws IOException {
        ContentIntelligenceEnrichmentProvider provider = new ContentIntelligenceEnrichmentProvider();
        Blob bmp = buildSyntheticImageBlob("bmp", "image/bmp", "sample.bmp");

        Blob transcoded = provider.transcodeIfNeeded(bmp);

        assertNotSame("BMP must be transcoded, not returned as-is", bmp, transcoded);
        assertEquals(ContentIntelligenceEnrichmentProvider.TRANSCODE_TARGET_MIME_TYPE, transcoded.getMimeType());
        assertEquals("sample.jpg", transcoded.getFilename());
        assertTrue("transcoded blob must carry real JPEG bytes", transcoded.getLength() > 0);
    }

    @Test
    public void shouldNotTranscodeNativelySupportedImageFormats() throws IOException {
        ContentIntelligenceEnrichmentProvider provider = new ContentIntelligenceEnrichmentProvider();
        for (String mime : ContentIntelligenceEnrichmentProvider.CIC_NATIVE_IMAGE_MIME_TYPES) {
            Blob original = new org.nuxeo.ecm.core.api.impl.blob.StringBlob("native", mime);
            assertSame("CIC-native image MIME " + mime + " must pass through unchanged", original,
                    provider.transcodeIfNeeded(original));
        }
    }

    @Test
    public void shouldNotTranscodeNonImageBlobs() {
        ContentIntelligenceEnrichmentProvider provider = new ContentIntelligenceEnrichmentProvider();
        Blob pdf = new org.nuxeo.ecm.core.api.impl.blob.StringBlob("not an image", "application/pdf");
        assertSame("document blobs must pass through unchanged", pdf, provider.transcodeIfNeeded(pdf));
    }

    @Test
    public void shouldNotTranscodeWhenMimeTypeIsMissing() {
        ContentIntelligenceEnrichmentProvider provider = new ContentIntelligenceEnrichmentProvider();
        Blob blank = new org.nuxeo.ecm.core.api.impl.blob.StringBlob("bytes", null);
        assertSame("blobs without a MIME type must pass through unchanged", blank, provider.transcodeIfNeeded(blank));
    }

    /**
     * If ImageIO has no reader for the given bytes (e.g. WebP without the TwelveMonkeys plugin), the provider must
     * fall back to sending the original blob and not crash the enrichment.
     */
    @Test
    public void shouldFallBackToOriginalWhenBytesAreNotDecodable() {
        ContentIntelligenceEnrichmentProvider provider = new ContentIntelligenceEnrichmentProvider();
        Blob garbage = new org.nuxeo.ecm.core.api.impl.blob.StringBlob("definitely-not-an-image", "image/bmp");
        Blob result = provider.transcodeIfNeeded(garbage);
        assertSame("undecodable bytes must round-trip unchanged so CIC can surface the error", garbage, result);
    }

    /**
     * End-to-end check: a BMP blob goes through {@link ContentIntelligenceEnrichmentProvider#enrich(BlobTextFromDocument)}
     * and reaches {@link HylandKEService} as a JPEG payload, which is exactly what the CIC API expects.
     */
    @Test
    public void enrichShouldSendTranscodedBlobForBmpInput() throws IOException {
        when(service.enrich(anyString(), any(Blob.class), anyList(), anyList(), nullable(String.class),
                nullable(String.class))).thenReturn(new ServiceCallResult(SUCCESS_RESPONSE));

        ContentIntelligenceEnrichmentProvider bmpProvider = new ContentIntelligenceEnrichmentProvider() {
            @Override
            protected HylandKEService getService() {
                return service;
            }

            @Override
            protected Blob resolveBlob(ManagedBlob managedBlob) {
                try {
                    return buildSyntheticImageBlob("bmp", "image/bmp", "sample.bmp");
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            }

            @Override
            public String saveJsonAsRawBlob(String rawJson) {
                return "test-raw-blob-key";
            }
        };
        bmpProvider.init(buildDescriptor(Map.of(
                ContentIntelligenceEnrichmentProvider.OPTION_CONFIG_NAME, "default",
                ContentIntelligenceEnrichmentProvider.OPTION_ACTIONS,
                "image-description,named-entity-recognition-image")));

        BlobTextFromDocument doc = buildBlobTextFromDoc("image/bmp");
        Collection<EnrichmentMetadata> metadata = bmpProvider.enrich(doc);
        assertEquals(1, metadata.size());

        org.mockito.ArgumentCaptor<Blob> sent = org.mockito.ArgumentCaptor.forClass(Blob.class);
        org.mockito.Mockito.verify(service)
                           .enrich(anyString(), sent.capture(), anyList(), anyList(), nullable(String.class),
                                   nullable(String.class));
        assertEquals("BMP must reach CIC as JPEG",
                ContentIntelligenceEnrichmentProvider.TRANSCODE_TARGET_MIME_TYPE, sent.getValue().getMimeType());
        assertNotEquals("transcoded payload must not be the BMP source", "image/bmp", sent.getValue().getMimeType());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // transcodeIfNeeded - edge cases
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldReturnNullWhenTranscodingNullBlob() {
        ContentIntelligenceEnrichmentProvider p = new ContentIntelligenceEnrichmentProvider();
        assertNull(p.transcodeIfNeeded(null));
    }

    @Test
    public void shouldFallBackToOriginalOnIOExceptionDuringTranscode() {
        ContentIntelligenceEnrichmentProvider p = new ContentIntelligenceEnrichmentProvider();
        Blob failBlob = new org.nuxeo.ecm.core.api.impl.blob.StringBlob("content", "image/gif") {
            @Override
            public java.io.InputStream getStream() throws IOException {
                throw new IOException("test I/O failure");
            }
        };
        failBlob.setFilename("broken.gif");
        Blob result = p.transcodeIfNeeded(failBlob);
        assertSame("I/O failure must return the original blob", failBlob, result);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // replaceExtension - edge cases
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldReturnDefaultFilenameWhenOriginalIsBlank() {
        assertEquals("image.jpg", provider.replaceExtension(null, "jpg"));
        assertEquals("image.jpg", provider.replaceExtension("", "jpg"));
        assertEquals("image.jpg", provider.replaceExtension("   ", "jpg"));
    }

    @Test
    public void shouldAppendExtensionWhenFilenameHasNoDot() {
        assertEquals("photo.jpg", provider.replaceExtension("photo", "jpg"));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // enrich - null blob from resolveBlob
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldSkipBlobWhenResolveReturnsNull() throws IOException {
        ContentIntelligenceEnrichmentProvider nullBlobProvider = new ContentIntelligenceEnrichmentProvider() {
            @Override
            protected HylandKEService getService() {
                return service;
            }

            @Override
            protected Blob resolveBlob(ManagedBlob managedBlob) {
                return null;
            }

            @Override
            public String saveJsonAsRawBlob(String rawJson) {
                return "key";
            }
        };
        nullBlobProvider.init(buildDescriptor(Map.of(
                ContentIntelligenceEnrichmentProvider.OPTION_ACTIONS, "image-description")));

        Collection<EnrichmentMetadata> metadata = nullBlobProvider.enrich(buildBlobTextFromDoc());
        assertEquals(0, metadata.size());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // handleFailedCall - null result
    // ---------------------------------------------------------------------------------------------------------------

    @Test(expected = NuxeoException.class)
    public void shouldThrowOnNullServiceResult() throws IOException {
        when(service.enrich(anyString(), any(Blob.class), anyList(), anyList(), nullable(String.class),
                nullable(String.class))).thenReturn(null);
        provider.enrich(buildBlobTextFromDoc());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // processResponse - results edge cases
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldReturnEmptyWhenResultsKeyIsMissing() throws IOException {
        String json = "{"
                + "\"responseCode\":200,\"responseMessage\":\"OK\","
                + "\"objectKeysMapping\":[],"
                + "\"response\":{\"id\":\"j\",\"status\":\"SUCCESS\"}"
                + "}";
        when(service.enrich(anyString(), any(Blob.class), anyList(), anyList(), nullable(String.class),
                nullable(String.class))).thenReturn(new ServiceCallResult(json));
        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc());
        assertEquals(0, metadata.size());
    }

    @Test
    public void shouldReturnEmptyWhenResultsArrayIsEmptyWithSuccessStatus() throws IOException {
        String json = "{"
                + "\"responseCode\":200,\"responseMessage\":\"OK\","
                + "\"objectKeysMapping\":[],"
                + "\"response\":{\"id\":\"j\",\"status\":\"SUCCESS\",\"results\":[]}"
                + "}";
        when(service.enrich(anyString(), any(Blob.class), anyList(), anyList(), nullable(String.class),
                nullable(String.class))).thenReturn(new ServiceCallResult(json));
        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc());
        assertEquals(0, metadata.size());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // toLabelSuggestions - edge cases
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldSkipNonObjectAndResultlessActionsInResults() throws IOException {
        String json = "{"
                + "\"responseCode\":200,\"responseMessage\":\"OK\","
                + "\"objectKeysMapping\":[],"
                + "\"response\":{\"id\":\"j\",\"status\":\"SUCCESS\","
                + "\"results\":[{\"objectKey\":\"ok1\","
                + "\"scalarField\":42,"
                + "\"noResult\":{\"isSuccess\":true},"
                + "\"validAction\":{\"isSuccess\":true,\"result\":{\"items\":[\"val1\"]}}"
                + "}]}}";
        when(service.enrich(anyString(), any(Blob.class), anyList(), anyList(), nullable(String.class),
                nullable(String.class))).thenReturn(new ServiceCallResult(json));

        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc());
        assertEquals(1, metadata.size());
        List<LabelSuggestion> suggestions = metadata.iterator().next().getLabels();
        assertEquals(1, suggestions.size());
        assertEquals("validAction", suggestions.get(0).getProperty());
    }

    @Test
    public void shouldFlattenNestedArrayAndObjectValuesInResults() throws IOException {
        String json = "{"
                + "\"responseCode\":200,\"responseMessage\":\"OK\","
                + "\"objectKeysMapping\":[],"
                + "\"response\":{\"id\":\"j\",\"status\":\"SUCCESS\","
                + "\"results\":[{\"objectKey\":\"ok1\","
                + "\"deepAction\":{\"isSuccess\":true,\"result\":[[\"nested-val\"],{\"key\":\"obj-val\"}]}"
                + "}]}}";
        when(service.enrich(anyString(), any(Blob.class), anyList(), anyList(), nullable(String.class),
                nullable(String.class))).thenReturn(new ServiceCallResult(json));

        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc());
        assertEquals(1, metadata.size());
        List<String> values = valuesOf(metadata.iterator().next().getLabels(), "deepAction");
        assertTrue(values.contains("nested-val"));
        assertTrue(values.contains("obj-val"));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // summarizeActionErrors - edge cases
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldSkipSuccessfulActionsInErrorSummary() {
        String json = "{\"results\":[{\"objectKey\":\"ok1\","
                + "\"okAction\":{\"isSuccess\":true,\"result\":\"fine\"},"
                + "\"failAction\":{\"isSuccess\":false,\"error\":{\"errorType\":\"E\",\"message\":\"m\"}}"
                + "}]}";
        org.json.JSONObject response = new org.json.JSONObject(json);
        String summary = provider.summarizeActionErrors(response);
        assertTrue(summary.contains("failAction"));
        assertFalse(summary.contains("okAction"));
    }

    @Test
    public void shouldHandleActionWithoutErrorBlock() {
        String json = "{\"results\":[{\"objectKey\":\"ok1\","
                + "\"brokenAction\":{\"isSuccess\":false}"
                + "}]}";
        org.json.JSONObject response = new org.json.JSONObject(json);
        String summary = provider.summarizeActionErrors(response);
        assertTrue("missing error block should use '?': " + summary, summary.contains("brokenAction[?]"));
    }

    @Test
    public void shouldSkipNonObjectActionEntriesInSummary() {
        String json = "{\"results\":[{\"objectKey\":\"ok1\","
                + "\"scalarAction\":42,"
                + "\"arrayAction\":[1,2,3],"
                + "\"realFail\":{\"isSuccess\":false,\"error\":{\"errorType\":\"E\",\"message\":\"m\"}}"
                + "}]}";
        org.json.JSONObject response = new org.json.JSONObject(json);
        String summary = provider.summarizeActionErrors(response);
        assertTrue(summary.contains("realFail"));
        assertFalse(summary.contains("scalarAction"));
        assertFalse(summary.contains("arrayAction"));
    }

    @Test
    public void shouldHandleActionErrorWithEmptyMessage() {
        String json = "{\"results\":[{\"objectKey\":\"ok1\","
                + "\"action\":{\"isSuccess\":false,\"error\":{\"errorType\":\"Timeout\"}}"
                + "}]}";
        org.json.JSONObject response = new org.json.JSONObject(json);
        String summary = provider.summarizeActionErrors(response);
        assertEquals("action[Timeout]", summary);
    }

    /** Builds a real {@code image/*} blob whose bytes are a valid solid-color picture in the given ImageIO format. */
    protected Blob buildSyntheticImageBlob(String imageioFormat, String mimeType, String filename) throws IOException {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.RED);
            g.fillRect(0, 0, 8, 8);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(img, imageioFormat, out)) {
            throw new IOException("ImageIO has no writer for format " + imageioFormat);
        }
        // Use ByteArrayBlob directly so the unit test does not need a running Nuxeo Framework for tmp-file creation.
        Blob blob = new org.nuxeo.ecm.core.api.impl.blob.ByteArrayBlob(out.toByteArray(), mimeType);
        blob.setFilename(filename);
        return blob;
    }

    /**
     * Builds an N x N RGB blob whose pixels are pseudo-random noise so the encoder cannot compress it down to a
     * handful of bytes. Used to drive {@link ContentIntelligenceEnrichmentProvider#transcodeIfNeeded(Blob)} over
     * the configurable byte budget on a deterministic fixture.
     */
    protected Blob buildNoisyImageBlob(int side, String imageioFormat, String mimeType, String filename)
            throws IOException {
        BufferedImage img = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        // Seeded so the test is deterministic but the pixels are non-uniform (no run-length compression wins).
        java.util.Random rng = new java.util.Random(0xC1CL);
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                img.setRGB(x, y, rng.nextInt() & 0xFFFFFF);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(img, imageioFormat, out)) {
            throw new IOException("ImageIO has no writer for format " + imageioFormat);
        }
        Blob blob = new org.nuxeo.ecm.core.api.impl.blob.ByteArrayBlob(out.toByteArray(), mimeType);
        blob.setFilename(filename);
        return blob;
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
