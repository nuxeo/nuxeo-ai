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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            + "\"imageClassification\":{\"isSuccess\":true,\"result\":\"test-class\"},"
            + "\"namedEntityImage\":{\"isSuccess\":true,\"result\":"
            + "{\"locations\":[\"New York\",\"Paris\"],\"persons\":[\"Jane Doe\"]}},"
            + "\"imageEmbeddings\":{\"isSuccess\":true,\"result\":[0.1,0.2]}"
            + "}"
            + "]"
            + "}}";

    /**
     * Same wrapper but with the {@code text-*} actions returned by Hyland CI for non-image blobs (PDFs, DOCX, PPTX,
     * XLSX, plain text, ...). Covers {@code textSummary}, {@code textClassification} and the entity buckets returned by
     * {@code named-entity-recognition-text}. A failed action entry is also included so we assert that {@code
     * isSuccess: false} blocks are skipped.
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
            + "\"textClassification\":{\"isSuccess\":true,\"result\":\"contract\"},"
            + "\"namedEntityText\":{\"isSuccess\":true,\"result\":"
            + "{\"organisations\":[\"Acme Corp\",\"Globex\"],\"persons\":[\"Jane Doe\"]}},"
            + "\"textEmbeddings\":{\"isSuccess\":true,\"result\":[0.11,0.42]},"
            + "\"textMetadataGeneration\":{\"isSuccess\":false,\"result\":null,\"error\":\"no KSimilarMetadata\"}"
            + "}"
            + "]"
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

    @Before
    public void setup() {
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
                return "test-raw-blob-key";
            }
        };
        provider.init(buildDescriptor(Map.of(
                ContentIntelligenceEnrichmentProvider.OPTION_CONFIG_NAME, "default",
                ContentIntelligenceEnrichmentProvider.OPTION_ACTIONS,
                "image-description,image-classification,image-embeddings,"
                        + "text-summarization,text-classification,named-entity-recognition-text")));
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

        // One suggestion per action, with the action as the suggestion property. imageDescription is intentionally
        // absent from the labels stream (the description listener pulls it from the raw blob), and numeric
        // imageEmbeddings never produces taggable values.
        Set<String> actions = suggestions.stream().map(LabelSuggestion::getProperty).collect(Collectors.toSet());
        assertEquals(Set.of("imageClassification", "namedEntityImage"), actions);

        // Label names carry only the clean value (no "action/" prefix) so downstream consumers can tag them as-is.
        List<String> classification = valuesOf(suggestions, "imageClassification");
        assertEquals(List.of("test-class"), classification);

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
    }

    @Test
    public void shouldReturnEmptyOnFailureStatus() throws IOException {
        when(service.enrich(anyString(), any(org.nuxeo.ecm.core.api.Blob.class), anyList(), anyList(),
                nullable(String.class), nullable(String.class))).thenReturn(new ServiceCallResult(FAILURE_RESPONSE));

        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc());
        assertEquals(0, metadata.size());
    }

    @Test
    public void shouldReturnEmptyOnHttpFailure() throws IOException {
        when(service.enrich(anyString(), any(org.nuxeo.ecm.core.api.Blob.class), anyList(), anyList(),
                nullable(String.class), nullable(String.class))).thenReturn(
                        new ServiceCallResult("{}", 500, "Internal Server Error"));

        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc());
        assertEquals(0, metadata.size());
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
    public void shouldRefuseConfigurationWithoutActions() {
        ContentIntelligenceEnrichmentProvider p = new ContentIntelligenceEnrichmentProvider();
        p.init(buildDescriptor(Map.of(ContentIntelligenceEnrichmentProvider.OPTION_ACTIONS, "")));
    }

    /**
     * Covers the document (PDF / DOCX / XLSX / PPTX / text) pipeline: verifies that {@code text-*} action results and
     * the {@code named-entity-recognition-text} entity buckets are flattened into the new per-action
     * {@link LabelSuggestion} shape. Description text ({@code textSummary}) must never reach the labels stream;
     * numeric {@code textEmbeddings} and the unsuccessful {@code textMetadataGeneration} block must also be dropped.
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
        // textClassification + namedEntityText only. textSummary (description) is delegated to the listener;
        // numeric textEmbeddings + unsuccessful textMetadataGeneration must stay out.
        Set<String> actions = suggestions.stream().map(LabelSuggestion::getProperty).collect(Collectors.toSet());
        assertEquals(Set.of("textClassification", "namedEntityText"), actions);

        assertEquals(List.of("contract"), valuesOf(suggestions, "textClassification"));

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

        // PROCESSING means the poll budget was exhausted before Hyland CI finished. We must drop the record instead
        // of claiming a partial result.
        Collection<EnrichmentMetadata> metadata = provider.enrich(buildBlobTextFromDoc("application/pdf"));
        assertEquals(0, metadata.size());
    }

    /**
     * The provider itself is MIME-type agnostic: the image pipe and the document pipe are separated at the stream
     * layer. This test ensures the same provider flow works when the blob is a PDF, a DOCX, an XLSX, a PPTX, or a
     * plain-text file. We drive the check with the text-oriented response to stay realistic.
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
            // We never expose the long-form summary as a label. Classification and entities are the taggable
            // signal for any text-bearing MIME type.
            assertTrue("textSummary must not leak as a label for " + mimeType,
                    suggestions.stream().noneMatch(s -> "textSummary".equals(s.getProperty())));
            assertTrue("expected textClassification for " + mimeType,
                    suggestions.stream().anyMatch(s -> "textClassification".equals(s.getProperty())));
        }
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
     * configurable - lets us exercise the provider against PDFs, Office formats and plain text without touching the
     * upstream Hyland connector.
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
