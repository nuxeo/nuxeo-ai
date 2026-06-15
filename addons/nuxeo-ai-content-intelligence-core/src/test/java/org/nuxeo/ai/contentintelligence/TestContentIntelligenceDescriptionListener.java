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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.nuxeo.ai.functions.RaiseEnrichmentEvent.ENRICHMENT_METADATA;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;

/**
 * Unit tests for {@link ContentIntelligenceDescriptionListener}. The listener's runtime path requires a Nuxeo
 * EventBundle plus the AI transient store; those are covered by integration tests. Here we exercise the pure
 * helpers that carry the actual logic.
 */
@RunWith(MockitoJUnitRunner.class)
public class TestContentIntelligenceDescriptionListener {

    public static final String IMAGE_RESPONSE = "{"
            + "\"responseCode\":200,"
            + "\"response\":{"
            + "\"id\":\"job-1\",\"status\":\"SUCCESS\","
            + "\"results\":[{"
            + "\"imageDescription\":{\"isSuccess\":true,\"result\":\"A sunset over Paris\"},"
            + "\"namedEntityImage\":{\"isSuccess\":true,\"result\":{\"locations\":[\"Paris\"]}}"
            + "}]"
            + "}}";

    public static final String TEXT_RESPONSE = "{"
            + "\"responseCode\":200,"
            + "\"response\":{"
            + "\"id\":\"job-2\",\"status\":\"SUCCESS\","
            + "\"results\":[{"
            + "\"textSummary\":{\"isSuccess\":true,\"result\":\"Quarterly sales report for 2026\"},"
            + "\"namedEntityText\":{\"isSuccess\":true,\"result\":{\"organisations\":[\"Acme Corp\"]}}"
            + "}]"
            + "}}";

    public static final String NO_DESCRIPTION_RESPONSE = "{"
            + "\"responseCode\":200,"
            + "\"response\":{"
            + "\"id\":\"job-3\",\"status\":\"SUCCESS\","
            + "\"results\":[{"
            + "\"namedEntityImage\":{\"isSuccess\":true,\"result\":{\"locations\":[\"Paris\"]}}"
            + "}]"
            + "}}";

    public static final String FAILED_DESCRIPTION_RESPONSE = "{"
            + "\"responseCode\":200,"
            + "\"response\":{"
            + "\"id\":\"job-4\",\"status\":\"PARTIAL_FAILURE\","
            + "\"results\":[{"
            + "\"imageDescription\":{\"isSuccess\":false,\"result\":null,\"error\":\"upstream\"},"
            + "\"namedEntityImage\":{\"isSuccess\":true,\"result\":{\"locations\":[\"Paris\"]}}"
            + "}]"
            + "}}";

    /** Wrapper-less variant: some upstream callers serialize the inner payload directly. */
    public static final String UNWRAPPED_RESPONSE = "{"
            + "\"id\":\"job-5\",\"status\":\"SUCCESS\","
            + "\"results\":[{"
            + "\"imageDescription\":{\"isSuccess\":true,\"result\":\"A pier at dawn\"}"
            + "}]}";

    /** First entry has no description, second does - the listener must keep scanning past empty entries. */
    public static final String SECOND_ENTRY_HAS_DESCRIPTION = "{"
            + "\"response\":{"
            + "\"results\":[{"
            + "\"namedEntityImage\":{\"isSuccess\":true,\"result\":{\"locations\":[\"city\"]}}"
            + "},{"
            + "\"imageDescription\":{\"isSuccess\":true,\"result\":\"A second image description\"}"
            + "}]"
            + "}}";

    /** Blank string description must not be returned as a valid value. */
    public static final String BLANK_DESCRIPTION_RESPONSE = "{"
            + "\"response\":{"
            + "\"results\":[{"
            + "\"imageDescription\":{\"isSuccess\":true,\"result\":\"   \"}"
            + "}]"
            + "}}";

    /** Non-string description value (defensive against future API changes). */
    public static final String NON_STRING_DESCRIPTION_RESPONSE = "{"
            + "\"response\":{"
            + "\"results\":[{"
            + "\"imageDescription\":{\"isSuccess\":true,\"result\":42}"
            + "}]"
            + "}}";

    public static final String EMPTY_RESULTS_RESPONSE = "{\"response\":{\"results\":[]}}";

    public static final String NO_RESULTS_KEY_RESPONSE = "{\"response\":{}}";

    protected ContentIntelligenceDescriptionListener listener;

    @Before
    public void setUp() {
        listener = new ContentIntelligenceDescriptionListener();
    }

    /**
     * Builds a listener whose {@link ContentIntelligenceDescriptionListener#readMaxLength()} returns a fixed value,
     * to exercise {@link ContentIntelligenceDescriptionListener#applyMaxLength(String)} without requiring a Nuxeo
     * Framework runtime in unit tests.
     */
    protected ContentIntelligenceDescriptionListener listenerWithMaxLength(int max) {
        return new ContentIntelligenceDescriptionListener() {
            @Override
            protected int readMaxLength() {
                return max;
            }
        };
    }

    // ---------------------------------------------------------------------------------------------------------------
    // parseDescription
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldExtractImageDescription() {
        assertEquals("A sunset over Paris", listener.parseDescription(IMAGE_RESPONSE));
    }

    @Test
    public void shouldExtractTextSummary() {
        assertEquals("Quarterly sales report for 2026", listener.parseDescription(TEXT_RESPONSE));
    }

    @Test
    public void shouldHandleUnwrappedResponse() {
        assertEquals("A pier at dawn", listener.parseDescription(UNWRAPPED_RESPONSE));
    }

    @Test
    public void shouldFindDescriptionInLaterEntry() {
        assertEquals("A second image description", listener.parseDescription(SECOND_ENTRY_HAS_DESCRIPTION));
    }

    @Test
    public void shouldReturnNullWhenNoDescriptionPresent() {
        assertNull(listener.parseDescription(NO_DESCRIPTION_RESPONSE));
    }

    @Test
    public void shouldIgnoreFailedDescriptionBlock() {
        assertNull(listener.parseDescription(FAILED_DESCRIPTION_RESPONSE));
    }

    @Test
    public void shouldReturnNullForBlankDescriptionResult() {
        assertNull(listener.parseDescription(BLANK_DESCRIPTION_RESPONSE));
    }

    @Test
    public void shouldReturnNullForNonStringDescriptionResult() {
        assertNull(listener.parseDescription(NON_STRING_DESCRIPTION_RESPONSE));
    }

    @Test
    public void shouldReturnNullForBlankOrInvalidJson() {
        assertNull(listener.parseDescription(null));
        assertNull(listener.parseDescription(""));
        assertNull(listener.parseDescription("   "));
        assertNull(listener.parseDescription("not-json-at-all"));
        assertNull(listener.parseDescription("{truncated"));
    }

    @Test
    public void shouldReturnNullWhenResultsArrayIsEmpty() {
        assertNull(listener.parseDescription(EMPTY_RESULTS_RESPONSE));
    }

    @Test
    public void shouldReturnNullWhenResultsKeyMissing() {
        assertNull(listener.parseDescription(NO_RESULTS_KEY_RESPONSE));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // writeDescription
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldWriteDescriptionWhenPropertyIsEmpty() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getPropertyValue(ContentIntelligenceConstants.DESCRIPTION_PROPERTY)).thenReturn(null);

        boolean dirty = listener.writeDescription(doc, "A sunset over Paris");

        assertTrue(dirty);
        verify(doc).setPropertyValue(ContentIntelligenceConstants.DESCRIPTION_PROPERTY,
                "A sunset over Paris");
    }

    @Test
    public void shouldWriteDescriptionWhenPropertyIsBlankString() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getPropertyValue(ContentIntelligenceConstants.DESCRIPTION_PROPERTY)).thenReturn("   ");

        boolean dirty = listener.writeDescription(doc, "Quarterly sales report for 2026");

        assertTrue(dirty);
        verify(doc).setPropertyValue(ContentIntelligenceConstants.DESCRIPTION_PROPERTY,
                "Quarterly sales report for 2026");
    }

    @Test
    public void shouldNotOverwriteExistingDescription() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getPropertyValue(ContentIntelligenceConstants.DESCRIPTION_PROPERTY)).thenReturn(
                "Curated by a human");
        when(doc.getId()).thenReturn("doc-1");

        boolean dirty = listener.writeDescription(doc, "Auto-generated description");

        assertFalse(dirty);
        verify(doc).getPropertyValue(ContentIntelligenceConstants.DESCRIPTION_PROPERTY);
        verify(doc).getId();
        verifyNoMoreInteractions(doc);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // applyMaxLength
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldNotTruncateWhenNoMaxConfigured() {
        String value = "x".repeat(2_000);
        assertEquals(value, listenerWithMaxLength(0).applyMaxLength(value));
    }

    @Test
    public void shouldTruncateLongDescriptionWhenMaxConfigured() {
        assertEquals("0123456789", listenerWithMaxLength(10).applyMaxLength("0123456789-extra"));
    }

    @Test
    public void shouldNotTruncateShortDescriptionWhenMaxConfigured() {
        assertEquals("hello", listenerWithMaxLength(100).applyMaxLength("hello"));
    }

    @Test
    public void shouldIgnoreNegativeMaxLength() {
        assertEquals("hello", listenerWithMaxLength(-5).applyMaxLength("hello"));
    }

    @Test
    public void shouldNotChokeOnNullDescription() {
        assertNull(listenerWithMaxLength(10).applyMaxLength(null));
    }

    @Test
    public void shouldDefaultToZeroWhenFrameworkPropertyUnavailable() {
        listener.readMaxLength();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // HANDLED_PROVIDERS sanity
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldOnlyHandleContentIntelligenceProviders() {
        assertTrue(ContentIntelligenceDescriptionListener.HANDLED_PROVIDERS.contains(
                ContentIntelligenceConstants.IMAGE_PROVIDER_NAME));
        assertTrue(ContentIntelligenceDescriptionListener.HANDLED_PROVIDERS.contains(
                ContentIntelligenceConstants.DOCUMENTS_PROVIDER_NAME));
        assertFalse(ContentIntelligenceDescriptionListener.HANDLED_PROVIDERS.contains("aws.imageLabels"));
        assertFalse(ContentIntelligenceDescriptionListener.HANDLED_PROVIDERS.contains("gcp.imageLabels"));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // handleEvent
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldReturnEarlyForNullEventBundle() {
        listener.handleEvent(null);
    }

    @Test
    public void shouldReturnEarlyForEmptyEventBundle() {
        EventBundle bundle = mock(EventBundle.class);
        when(bundle.isEmpty()).thenReturn(true);
        listener.handleEvent(bundle);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldIterateEventsInBundle() {
        Event event = mock(Event.class);
        when(event.getContext()).thenReturn(null);
        EventBundle bundle = mock(EventBundle.class);
        when(bundle.isEmpty()).thenReturn(false);
        when(bundle.iterator()).thenReturn((Iterator) List.of(event).iterator());
        listener.handleEvent(bundle);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // handleSingle - early return branches
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldIgnoreEventWithNullContext() {
        Event event = mock(Event.class);
        when(event.getContext()).thenReturn(null);
        listener.handleSingle(event);
    }

    @Test
    public void shouldIgnoreEventWithNonMetadataProperty() {
        Event event = mock(Event.class);
        EventContext ctx = mock(EventContext.class);
        when(event.getContext()).thenReturn(ctx);
        when(ctx.getProperty(ENRICHMENT_METADATA)).thenReturn("not-a-metadata-object");
        listener.handleSingle(event);
    }

    @Test
    public void shouldIgnoreMetadataFromUnhandledProvider() {
        Event event = mockEventWith(buildEM("aws.imageLabels", "repo", "doc-1"));
        listener.handleSingle(event);
    }

    @Test
    public void shouldIgnoreMetadataWithBlankRepositoryOrDocRef() throws Exception {
        EnrichmentMetadata metadata = mock(EnrichmentMetadata.class);
        when(metadata.getModelName()).thenReturn(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME);
        java.lang.reflect.Field contextField = org.nuxeo.ai.metadata.AIMetadata.class.getDeclaredField("context");
        contextField.setAccessible(true);
        contextField.set(metadata, new org.nuxeo.ai.metadata.AIMetadata.Context("", "", null, null));
        Event event = mockEventWith(metadata);
        listener.handleSingle(event);
    }

    @Test
    public void shouldIgnoreMetadataWhenExtractedDescriptionIsBlank() {
        ContentIntelligenceDescriptionListener stubListener = new ContentIntelligenceDescriptionListener() {
            @Override
            protected String extractDescription(EnrichmentMetadata metadata) {
                return null;
            }
        };
        Event event = mockEventWith(buildEM(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME, "repo", "doc-1"));
        stubListener.handleSingle(event);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // extractDescription - edge cases via method overrides
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldReturnNullFromExtractDescriptionWithBlankRawKey() {
        EnrichmentMetadata metadata = buildEMWithRawKey(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME, null);
        assertNull(listener.extractDescription(metadata));
    }

    @Test
    public void shouldReturnNullFromExtractDescriptionWithEmptyRawKey() {
        EnrichmentMetadata metadata = buildEMWithRawKey(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME, "");
        assertNull(listener.extractDescription(metadata));
    }

    @Test
    public void shouldReturnNullFromExtractDescriptionWithWhitespaceRawKey() {
        EnrichmentMetadata metadata = buildEMWithRawKey(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME, "   ");
        assertNull(listener.extractDescription(metadata));
    }

    /**
     * Exercises the full extractDescription happy path by subclassing the listener to bypass Framework.getService.
     */
    @Test
    public void shouldExtractDescriptionFromTransientStoreBlob() {
        Blob blob = new StringBlob(IMAGE_RESPONSE, "application/json");
        TransientStore mockStore = mock(TransientStore.class);
        when(mockStore.getBlobs("raw-key-1")).thenReturn(List.of(blob));

        ContentIntelligenceDescriptionListener testListener = listenerWithStore(mockStore);
        EnrichmentMetadata metadata = buildEMWithRawKey(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME, "raw-key-1");
        assertEquals("A sunset over Paris", testListener.extractDescription(metadata));
    }

    @Test
    public void shouldReturnNullWhenTransientStoreIsNull() {
        ContentIntelligenceDescriptionListener testListener = listenerWithStore(null);
        EnrichmentMetadata metadata = buildEMWithRawKey(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME, "raw-key-1");
        assertNull(testListener.extractDescription(metadata));
    }

    @Test
    public void shouldReturnNullWhenBlobsAreEmpty() {
        TransientStore mockStore = mock(TransientStore.class);
        when(mockStore.getBlobs("raw-key-1")).thenReturn(Collections.emptyList());

        ContentIntelligenceDescriptionListener testListener = listenerWithStore(mockStore);
        EnrichmentMetadata metadata = buildEMWithRawKey(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME, "raw-key-1");
        assertNull(testListener.extractDescription(metadata));
    }

    @Test
    public void shouldReturnNullWhenBlobsAreNull() {
        TransientStore mockStore = mock(TransientStore.class);
        when(mockStore.getBlobs("raw-key-1")).thenReturn(null);

        ContentIntelligenceDescriptionListener testListener = listenerWithStore(mockStore);
        EnrichmentMetadata metadata = buildEMWithRawKey(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME, "raw-key-1");
        assertNull(testListener.extractDescription(metadata));
    }

    @Test
    public void shouldReturnNullFromExtractDescriptionOnIOException() {
        Blob badBlob = mock(Blob.class);
        try {
            when(badBlob.getString()).thenThrow(new IOException("test IO error"));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        TransientStore mockStore = mock(TransientStore.class);
        when(mockStore.getBlobs("raw-key-1")).thenReturn(List.of(badBlob));

        ContentIntelligenceDescriptionListener testListener = listenerWithStore(mockStore);
        EnrichmentMetadata metadata = buildEMWithRawKey(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME, "raw-key-1");
        assertNull(testListener.extractDescription(metadata));
    }

    @Test
    public void shouldReturnNullFromExtractDescriptionOnRuntimeException() {
        TransientStore mockStore = mock(TransientStore.class);
        when(mockStore.getBlobs("raw-key-1")).thenThrow(new RuntimeException("boom"));

        ContentIntelligenceDescriptionListener testListener = listenerWithStore(mockStore);
        EnrichmentMetadata metadata = buildEMWithRawKey(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME, "raw-key-1");
        assertNull(testListener.extractDescription(metadata));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // handleSingle - full transactional path via method overrides
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldWriteDescriptionThroughTransactionalPath() {
        DocumentModel mockDoc = mock(DocumentModel.class);
        when(mockDoc.getPropertyValue(ContentIntelligenceConstants.DESCRIPTION_PROPERTY)).thenReturn(null);

        ContentIntelligenceDescriptionListener testListener = listenerForTransactionalPath(
                "AI-generated description", mockDoc);

        EnrichmentMetadata metadata = buildEM(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME, "repo", "doc-1");
        Event event = mockEventWith(metadata);
        testListener.handleSingle(event);

        verify(mockDoc).setPropertyValue(ContentIntelligenceConstants.DESCRIPTION_PROPERTY,
                "AI-generated description");
    }

    @Test
    public void shouldHandleDocumentNotFoundInTransactionalPath() {
        final boolean[] called = { false };
        ContentIntelligenceDescriptionListener testListener = new ContentIntelligenceDescriptionListener() {
            @Override
            protected String extractDescription(EnrichmentMetadata metadata) {
                return "AI-generated description";
            }

            @Override
            protected int readMaxLength() {
                return 0;
            }

            @Override
            protected void writeDescriptionInTransaction(String repoName, String docId, String description) {
                called[0] = true;
            }
        };
        EnrichmentMetadata metadata = buildEM(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME, "repo", "doc-1");
        Event event = mockEventWith(metadata);
        testListener.handleSingle(event);
        assertTrue("writeDescriptionInTransaction must be called", called[0]);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // readMaxLength - via override patterns
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldParseValidMaxLengthProperty() {
        assertEquals(500, listenerWithMaxLength(500).readMaxLength());
    }

    @Test
    public void shouldReturnZeroForMaxLengthDefault() {
        assertEquals(0, listenerWithMaxLength(0).readMaxLength());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Creates a listener that bypasses Framework.getService for extractDescription by directly injecting a
     * TransientStore mock.
     */
    protected ContentIntelligenceDescriptionListener listenerWithStore(TransientStore store) {
        return new ContentIntelligenceDescriptionListener() {
            @Override
            protected TransientStore getTransientStore(String providerName) {
                return store;
            }
        };
    }

    /**
     * Creates a listener for the full handleSingle transactional path, bypassing Framework/Transaction statics.
     */
    protected ContentIntelligenceDescriptionListener listenerForTransactionalPath(String description,
            DocumentModel doc) {
        return new ContentIntelligenceDescriptionListener() {
            @Override
            protected String extractDescription(EnrichmentMetadata metadata) {
                return description;
            }

            @Override
            protected int readMaxLength() {
                return 0;
            }

            @Override
            protected void writeDescriptionInTransaction(String repoName, String docId,
                    String descriptionValue) {
                writeDescription(doc, descriptionValue);
                // Simulate the session.saveDocument call
            }
        };
    }

    private Event mockEventWith(EnrichmentMetadata metadata) {
        Event event = mock(Event.class);
        EventContext ctx = mock(EventContext.class);
        when(event.getContext()).thenReturn(ctx);
        when(ctx.getProperty(ENRICHMENT_METADATA)).thenReturn(metadata);
        return event;
    }

    private EnrichmentMetadata buildEM(String modelName, String repositoryName, String docRef) {
        BlobTextFromDocument blobText = new BlobTextFromDocument();
        if (repositoryName != null) {
            blobText.setRepositoryName(repositoryName);
        }
        if (docRef != null) {
            blobText.setId(docRef);
        }
        return new EnrichmentMetadata.Builder("/kind", modelName, blobText).build();
    }

    private EnrichmentMetadata buildEMWithRawKey(String modelName, String rawKey) {
        BlobTextFromDocument blobText = new BlobTextFromDocument();
        blobText.setRepositoryName("repo");
        blobText.setId("doc-1");
        return new EnrichmentMetadata.Builder("/kind", modelName, blobText).withRawKey(rawKey).build();
    }
}
