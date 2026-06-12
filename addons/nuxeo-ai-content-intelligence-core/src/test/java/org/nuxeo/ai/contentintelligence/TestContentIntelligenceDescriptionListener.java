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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.nuxeo.ai.functions.RaiseEnrichmentEvent.ENRICHMENT_METADATA;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

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
        // In a plain unit-test context Framework may not be initialised; readMaxLength must degrade gracefully.
        // We don't assert a specific value (it depends on the surrounding test runner), only that no exception is
        // raised.
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

    @Test
    @SuppressWarnings("unchecked")
    public void shouldWriteDescriptionThroughTransactionalPath() {
        ContentIntelligenceDescriptionListener testListener = new ContentIntelligenceDescriptionListener() {
            @Override
            protected String extractDescription(EnrichmentMetadata metadata) {
                return "AI-generated description";
            }

            @Override
            protected int readMaxLength() {
                return 0;
            }
        };
        EnrichmentMetadata metadata = buildEM(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME, "repo", "doc-1");
        Event event = mockEventWith(metadata);

        CoreSession mockSession = mock(CoreSession.class);
        DocumentModel mockDoc = mock(DocumentModel.class);
        when(mockSession.getDocument(any(IdRef.class))).thenReturn(mockDoc);
        when(mockDoc.getPropertyValue(ContentIntelligenceConstants.DESCRIPTION_PROPERTY)).thenReturn(null);

        try (MockedStatic<TransactionHelper> th = mockStatic(TransactionHelper.class);
                MockedStatic<CoreInstance> ci = mockStatic(CoreInstance.class)) {
            th.when(() -> TransactionHelper.runInTransaction(any(Runnable.class))).thenAnswer(inv -> {
                ((Runnable) inv.getArgument(0)).run();
                return null;
            });
            ci.when(() -> CoreInstance.doPrivileged(anyString(), any(Consumer.class))).thenAnswer(inv -> {
                ((Consumer<CoreSession>) inv.getArgument(1)).accept(mockSession);
                return null;
            });
            testListener.handleSingle(event);
        }
        verify(mockDoc).setPropertyValue(ContentIntelligenceConstants.DESCRIPTION_PROPERTY,
                "AI-generated description");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldHandleDocumentNotFoundInTransactionalPath() {
        ContentIntelligenceDescriptionListener testListener = new ContentIntelligenceDescriptionListener() {
            @Override
            protected String extractDescription(EnrichmentMetadata metadata) {
                return "AI-generated description";
            }

            @Override
            protected int readMaxLength() {
                return 0;
            }
        };
        EnrichmentMetadata metadata = buildEM(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME, "repo", "doc-1");
        Event event = mockEventWith(metadata);

        CoreSession mockSession = mock(CoreSession.class);
        when(mockSession.getDocument(any(IdRef.class))).thenThrow(new DocumentNotFoundException("not found"));

        try (MockedStatic<TransactionHelper> th = mockStatic(TransactionHelper.class);
                MockedStatic<CoreInstance> ci = mockStatic(CoreInstance.class)) {
            th.when(() -> TransactionHelper.runInTransaction(any(Runnable.class))).thenAnswer(inv -> {
                ((Runnable) inv.getArgument(0)).run();
                return null;
            });
            ci.when(() -> CoreInstance.doPrivileged(anyString(), any(Consumer.class))).thenAnswer(inv -> {
                ((Consumer<CoreSession>) inv.getArgument(1)).accept(mockSession);
                return null;
            });
            testListener.handleSingle(event);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // extractDescription
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldReturnNullFromExtractDescriptionWithBlankRawKey() {
        EnrichmentMetadata metadata = buildEMWithRawKey(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME, null);
        assertNull(listener.extractDescription(metadata));
    }

    @Test
    public void shouldReturnNullWhenAIComponentIsNull() {
        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            fw.when(() -> Framework.getService(AIComponent.class)).thenReturn(null);
            EnrichmentMetadata metadata = buildEMWithRawKey(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME,
                    "raw-key-1");
            assertNull(listener.extractDescription(metadata));
        }
    }

    @Test
    public void shouldReturnNullWhenTransientStoreIsNull() {
        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            AIComponent aiComponent = mock(AIComponent.class);
            fw.when(() -> Framework.getService(AIComponent.class)).thenReturn(aiComponent);
            when(aiComponent.getTransientStoreForEnrichmentProvider(anyString())).thenReturn(null);
            EnrichmentMetadata metadata = buildEMWithRawKey(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME,
                    "raw-key-1");
            assertNull(listener.extractDescription(metadata));
        }
    }

    @Test
    public void shouldReturnNullWhenBlobsAreEmpty() {
        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            AIComponent aiComponent = mock(AIComponent.class);
            TransientStore store = mock(TransientStore.class);
            fw.when(() -> Framework.getService(AIComponent.class)).thenReturn(aiComponent);
            when(aiComponent.getTransientStoreForEnrichmentProvider(anyString())).thenReturn(store);
            when(store.getBlobs("raw-key-1")).thenReturn(Collections.emptyList());
            EnrichmentMetadata metadata = buildEMWithRawKey(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME,
                    "raw-key-1");
            assertNull(listener.extractDescription(metadata));
        }
    }

    @Test
    public void shouldReturnNullWhenBlobsAreNull() {
        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            AIComponent aiComponent = mock(AIComponent.class);
            TransientStore store = mock(TransientStore.class);
            fw.when(() -> Framework.getService(AIComponent.class)).thenReturn(aiComponent);
            when(aiComponent.getTransientStoreForEnrichmentProvider(anyString())).thenReturn(store);
            when(store.getBlobs("raw-key-1")).thenReturn(null);
            EnrichmentMetadata metadata = buildEMWithRawKey(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME,
                    "raw-key-1");
            assertNull(listener.extractDescription(metadata));
        }
    }

    @Test
    public void shouldReturnNullFromExtractDescriptionOnRuntimeException() {
        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            fw.when(() -> Framework.getService(AIComponent.class)).thenThrow(new RuntimeException("boom"));
            EnrichmentMetadata metadata = buildEMWithRawKey(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME,
                    "raw-key-1");
            assertNull(listener.extractDescription(metadata));
        }
    }

    @Test
    public void shouldExtractDescriptionFromTransientStoreBlob() {
        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            AIComponent aiComponent = mock(AIComponent.class);
            TransientStore store = mock(TransientStore.class);
            fw.when(() -> Framework.getService(AIComponent.class)).thenReturn(aiComponent);
            when(aiComponent.getTransientStoreForEnrichmentProvider(anyString())).thenReturn(store);
            Blob blob = new StringBlob(IMAGE_RESPONSE, "application/json");
            when(store.getBlobs("raw-key-1")).thenReturn(List.of(blob));
            EnrichmentMetadata metadata = buildEMWithRawKey(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME,
                    "raw-key-1");
            assertEquals("A sunset over Paris", listener.extractDescription(metadata));
        }
    }

    @Test
    public void shouldReturnNullFromExtractDescriptionOnIOException() {
        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            AIComponent aiComponent = mock(AIComponent.class);
            TransientStore store = mock(TransientStore.class);
            fw.when(() -> Framework.getService(AIComponent.class)).thenReturn(aiComponent);
            when(aiComponent.getTransientStoreForEnrichmentProvider(anyString())).thenReturn(store);
            Blob badBlob = mock(Blob.class);
            try {
                when(badBlob.getString()).thenThrow(new IOException("test IO error"));
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            when(store.getBlobs("raw-key-1")).thenReturn(List.of(badBlob));
            EnrichmentMetadata metadata = buildEMWithRawKey(ContentIntelligenceConstants.IMAGE_PROVIDER_NAME,
                    "raw-key-1");
            assertNull(listener.extractDescription(metadata));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // readMaxLength - Framework property paths
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldParseValidMaxLengthProperty() {
        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            fw.when(() -> Framework.getProperty(ContentIntelligenceDescriptionListener.MAX_LENGTH_PROP))
              .thenReturn("500");
            assertEquals(500, listener.readMaxLength());
        }
    }

    @Test
    public void shouldReturnZeroForInvalidMaxLengthProperty() {
        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            fw.when(() -> Framework.getProperty(ContentIntelligenceDescriptionListener.MAX_LENGTH_PROP))
              .thenReturn("not-a-number");
            assertEquals(0, listener.readMaxLength());
        }
    }

    @Test
    public void shouldReturnZeroForBlankMaxLengthProperty() {
        try (MockedStatic<Framework> fw = mockStatic(Framework.class)) {
            fw.when(() -> Framework.getProperty(ContentIntelligenceDescriptionListener.MAX_LENGTH_PROP))
              .thenReturn("  ");
            assertEquals(0, listener.readMaxLength());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------------------------

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
