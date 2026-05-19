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
 * EventBundle plus the AI transient store; those are covered by integration tests. Here we exercise the two pure
 * helpers that carry the actual logic:
 * <ul>
 *   <li>{@link ContentIntelligenceDescriptionListener#parseDescription(String)} - extracting the description /
 *       summary value from the wrapped Hyland CI JSON.</li>
 *   <li>{@link ContentIntelligenceDescriptionListener#writeDescription(DocumentModel, String)} - guarded write
 *       semantics against {@code dc:description}.</li>
 * </ul>
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

    protected ContentIntelligenceDescriptionListener listener;

    @Before
    public void setUp() {
        listener = new ContentIntelligenceDescriptionListener();
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
        // The provider stores the wrapped form, but be defensive against payloads serialised without the
        // outer "response" envelope.
        assertEquals("A pier at dawn", listener.parseDescription(UNWRAPPED_RESPONSE));
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
    public void shouldReturnNullForBlankOrInvalidJson() {
        assertNull(listener.parseDescription(null));
        assertNull(listener.parseDescription(""));
        assertNull(listener.parseDescription("   "));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // writeDescription
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldWriteDescriptionWhenPropertyIsEmpty() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getPropertyValue(ContentIntelligenceDescriptionListener.DESCRIPTION_PROPERTY)).thenReturn(null);

        boolean dirty = listener.writeDescription(doc, "A sunset over Paris");

        assertTrue(dirty);
        verify(doc).setPropertyValue(ContentIntelligenceDescriptionListener.DESCRIPTION_PROPERTY,
                "A sunset over Paris");
    }

    @Test
    public void shouldWriteDescriptionWhenPropertyIsBlankString() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getPropertyValue(ContentIntelligenceDescriptionListener.DESCRIPTION_PROPERTY)).thenReturn("   ");

        boolean dirty = listener.writeDescription(doc, "Quarterly sales report for 2026");

        assertTrue(dirty);
        verify(doc).setPropertyValue(ContentIntelligenceDescriptionListener.DESCRIPTION_PROPERTY,
                "Quarterly sales report for 2026");
    }

    @Test
    public void shouldNotOverwriteExistingDescription() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getPropertyValue(ContentIntelligenceDescriptionListener.DESCRIPTION_PROPERTY)).thenReturn(
                "Curated by a human");
        when(doc.getId()).thenReturn("doc-1");

        boolean dirty = listener.writeDescription(doc, "Auto-generated description");

        assertFalse(dirty);
        // Only the read happened - we must never call setPropertyValue on a doc that already has a description.
        verify(doc).getPropertyValue(ContentIntelligenceDescriptionListener.DESCRIPTION_PROPERTY);
        verify(doc).getId();
        verifyNoMoreInteractions(doc);
    }
}
