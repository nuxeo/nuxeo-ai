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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Unit tests for {@link StoreContentIntelligenceMetadata}. The consumer's hot path is the mapping between Hyland CI
 * labels (e.g. {@code imageDescription/...}, {@code textSummary/...}, {@code namedEntityText/...}) and the target
 * document properties ({@code dc:description} + {@code dc:tags}). All of that logic lives in protected helpers, so we
 * test them directly without needing a Nuxeo runtime.
 */
@RunWith(MockitoJUnitRunner.class)
public class TestStoreContentIntelligenceMetadata {

    protected StoreContentIntelligenceMetadata consumer;

    @Before
    public void setUp() {
        consumer = new StoreContentIntelligenceMetadata();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // splitActionValue / sanitizeTag - low level helpers
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldSplitActionAndValueOnFirstSlash() {
        assertArrayEquals(new String[] { "imageDescription", "A sunny day in Paris" },
                consumer.splitActionValue("imageDescription/A sunny day in Paris"));
        // Only the first slash counts: paths that happen to contain a slash keep it in the value half.
        assertArrayEquals(new String[] { "textSummary", "Clause 1/2: indemnity" },
                consumer.splitActionValue("textSummary/Clause 1/2: indemnity"));
    }

    @Test
    public void shouldTreatLabelWithoutSlashAsActionOnly() {
        assertArrayEquals(new String[] { "legacyAction", "" }, consumer.splitActionValue("legacyAction"));
    }

    @Test
    public void shouldHyphenateMultiWordTags() {
        // Case is preserved - sanitizeTag only hyphenates whitespace and strips unsafe chars.
        assertEquals("New-York-City", consumer.sanitizeTag("New York City"));
        assertEquals("Jane-Doe", consumer.sanitizeTag(" Jane   Doe "));
    }

    @Test
    public void shouldStripUnsafeCharactersFromTags() {
        // /, ', \ and % are rejected by the Nuxeo TagService.
        assertEquals("acmecorp", consumer.sanitizeTag("acme/corp"));
        assertEquals("ODonnell", consumer.sanitizeTag("O'Donnell"));
        assertEquals("50-percent", consumer.sanitizeTag("50 %percent"));
        assertEquals("back-slash", consumer.sanitizeTag("back\\ slash"));
    }

    @Test
    public void shouldReturnNullForBlankSanitizerInput() {
        assertNull(consumer.sanitizeTag(null));
        assertNull(consumer.sanitizeTag(""));
        assertNull(consumer.sanitizeTag("   "));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // firstValueForActions - picks the description source
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldPreferImageDescriptionWhenPresent() {
        EnrichmentMetadata metadata = buildMetadata(label("imageDescription", "A skyline at dusk"),
                label("imageClassification", "landscape"));
        assertEquals("A skyline at dusk",
                consumer.firstValueForActions(metadata, StoreContentIntelligenceMetadata.DESCRIPTION_ACTIONS));
    }

    @Test
    public void shouldFallBackToTextSummaryForDocumentBlobs() {
        EnrichmentMetadata metadata = buildMetadata(label("textSummary", "Supply contract with Acme Corp"),
                label("textClassification", "contract"),
                label("namedEntityText", "Jane Doe"));
        assertEquals("Supply contract with Acme Corp",
                consumer.firstValueForActions(metadata, StoreContentIntelligenceMetadata.DESCRIPTION_ACTIONS));
    }

    @Test
    public void shouldReturnNullWhenNoDescriptionLabelPresent() {
        EnrichmentMetadata metadata = buildMetadata(label("imageClassification", "landscape"),
                label("namedEntityText", "Jane Doe"));
        assertNull(consumer.firstValueForActions(metadata, StoreContentIntelligenceMetadata.DESCRIPTION_ACTIONS));
    }

    @Test
    public void shouldHandleMetadataWithoutLabels() {
        BlobTextFromDocument blobText = buildBlobText();
        EnrichmentMetadata empty = new EnrichmentMetadata.Builder("/classification/imageLabels", "test", blobText)
                                                                                                                .withLabels(
                                                                                                                        Collections.emptyList())
                                                                                                                .build();
        assertNull(consumer.firstValueForActions(empty, StoreContentIntelligenceMetadata.DESCRIPTION_ACTIONS));
        assertTrue(consumer.collectTagValues(empty).isEmpty());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // collectTagValues - description actions must be filtered out; everything else sanitised into tags
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldCollectEveryNonDescriptionLabelAsATag() {
        EnrichmentMetadata metadata = buildMetadata(label("textSummary", "A supply contract"),
                label("textClassification", "contract"),
                label("namedEntityText", "Acme Corp"),
                label("namedEntityText", "Jane Doe"));

        Set<String> tags = consumer.collectTagValues(metadata);
        // textSummary must NOT appear - descriptions are never tagged.
        assertFalse(tags.contains("A-supply-contract"));
        // The remaining three values must all be present, sanitised (multi-word -> hyphens, case preserved).
        assertTrue(tags.contains("contract"));
        assertTrue(tags.contains("Acme-Corp"));
        assertTrue(tags.contains("Jane-Doe"));
        assertEquals(3, tags.size());
    }

    @Test
    public void shouldDeduplicateRepeatedLabelValues() {
        EnrichmentMetadata metadata = buildMetadata(label("imageClassification", "landscape"),
                label("namedEntityImage", "landscape"));
        Set<String> tags = consumer.collectTagValues(metadata);
        assertEquals(1, tags.size());
        assertTrue(tags.contains("landscape"));
    }

    @Test
    public void shouldIgnoreBothImageAndTextDescriptionActionsWhenTagging() {
        EnrichmentMetadata metadata = buildMetadata(label("imageDescription", "A sunset"),
                label("textSummary", "A contract"),
                label("imageClassification", "landscape"));
        Set<String> tags = consumer.collectTagValues(metadata);
        // Only imageClassification should survive - both description actions are excluded.
        assertEquals(Collections.singleton("landscape"), tags);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // applyDescription - full behavioural test against a mocked DocumentModel
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldWriteImageDescriptionWhenPropertyIsEmpty() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getPropertyValue(StoreContentIntelligenceMetadata.DESCRIPTION_PROPERTY)).thenReturn(null);

        boolean dirty = consumer.applyDescription(doc, buildMetadata(label("imageDescription", "A sunset in Paris")));

        assertTrue(dirty);
        verify(doc).setPropertyValue(StoreContentIntelligenceMetadata.DESCRIPTION_PROPERTY, "A sunset in Paris");
    }

    @Test
    public void shouldWriteTextSummaryWhenPropertyIsEmpty() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getPropertyValue(StoreContentIntelligenceMetadata.DESCRIPTION_PROPERTY)).thenReturn("");

        boolean dirty = consumer.applyDescription(doc,
                buildMetadata(label("textSummary", "Quarterly sales report for 2026")));

        assertTrue(dirty);
        verify(doc).setPropertyValue(StoreContentIntelligenceMetadata.DESCRIPTION_PROPERTY,
                "Quarterly sales report for 2026");
    }

    @Test
    public void shouldNotOverwriteExistingDescription() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getPropertyValue(StoreContentIntelligenceMetadata.DESCRIPTION_PROPERTY)).thenReturn(
                "Curated by a human");
        when(doc.getId()).thenReturn("doc-1");

        boolean dirty = consumer.applyDescription(doc,
                buildMetadata(label("imageDescription", "Auto-generated description")));

        assertFalse(dirty);
        // Only the read happened - we must never call setPropertyValue on a doc that already has a description.
        verify(doc).getPropertyValue(StoreContentIntelligenceMetadata.DESCRIPTION_PROPERTY);
        verify(doc).getId();
        verifyNoMoreInteractions(doc);
    }

    @Test
    public void shouldReturnFalseWhenNoDescriptionLabel() {
        DocumentModel doc = mock(DocumentModel.class);
        EnrichmentMetadata metadata = buildMetadata(label("imageClassification", "landscape"),
                label("namedEntityText", "Jane Doe"));

        assertFalse(consumer.applyDescription(doc, metadata));
        // No property reads, no writes - we short-circuit before touching the doc at all.
        verifyNoMoreInteractions(doc);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------------------------

    protected BlobTextFromDocument buildBlobText() {
        BlobTextFromDocument blobText = new BlobTextFromDocument();
        blobText.setRepositoryName("test");
        blobText.setId("doc-1");
        return blobText;
    }

    /** Packs the given {@code action/value} labels into a single {@link LabelSuggestion} ready for assertion. */
    protected EnrichmentMetadata buildMetadata(AIMetadata.Label... labels) {
        List<AIMetadata.Label> values = new ArrayList<>(Arrays.asList(labels));
        LabelSuggestion suggestion = new LabelSuggestion("file:content", values);
        return new EnrichmentMetadata.Builder("/classification/imageLabels", "test", buildBlobText())
                                                                                                    .withLabels(
                                                                                                            Collections.singletonList(
                                                                                                                    suggestion))
                                                                                                    .build();
    }

    protected AIMetadata.Label label(String action, String value) {
        return new AIMetadata.Label(action + "/" + value, 1.0F);
    }
}
