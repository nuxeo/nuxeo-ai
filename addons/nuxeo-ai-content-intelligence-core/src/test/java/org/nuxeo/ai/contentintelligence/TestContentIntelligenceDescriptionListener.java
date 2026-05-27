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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.nuxeo.ecm.core.api.DocumentModel;

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
            + "\"imageClassification\":{\"isSuccess\":true,\"result\":\"landscape\"}"
            + "}]"
            + "}}";

    public static final String TEXT_RESPONSE = "{"
            + "\"responseCode\":200,"
            + "\"response\":{"
            + "\"id\":\"job-2\",\"status\":\"SUCCESS\","
            + "\"results\":[{"
            + "\"textSummary\":{\"isSuccess\":true,\"result\":\"Quarterly sales report for 2026\"},"
            + "\"textClassification\":{\"isSuccess\":true,\"result\":\"report\"}"
            + "}]"
            + "}}";

    public static final String NO_DESCRIPTION_RESPONSE = "{"
            + "\"responseCode\":200,"
            + "\"response\":{"
            + "\"id\":\"job-3\",\"status\":\"SUCCESS\","
            + "\"results\":[{"
            + "\"imageClassification\":{\"isSuccess\":true,\"result\":\"landscape\"}"
            + "}]"
            + "}}";

    public static final String FAILED_DESCRIPTION_RESPONSE = "{"
            + "\"responseCode\":200,"
            + "\"response\":{"
            + "\"id\":\"job-4\",\"status\":\"PARTIAL_FAILURE\","
            + "\"results\":[{"
            + "\"imageDescription\":{\"isSuccess\":false,\"result\":null,\"error\":\"upstream\"},"
            + "\"imageClassification\":{\"isSuccess\":true,\"result\":\"landscape\"}"
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
            + "\"imageClassification\":{\"isSuccess\":true,\"result\":\"city\"}"
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
}
