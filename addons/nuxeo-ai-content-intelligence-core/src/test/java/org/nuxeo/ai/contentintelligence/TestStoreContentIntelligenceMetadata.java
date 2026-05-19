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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

/**
 * Unit tests for {@link StoreContentIntelligenceMetadata}. The consumer is now strictly a tag-writer: long-form
 * description / summary text is delegated to {@link ContentIntelligenceDescriptionListener} and must never reach this
 * code path through the labels stream. These tests therefore focus on:
 * <ul>
 *   <li>{@link StoreContentIntelligenceMetadata#sanitizeTag(String)} - tag string normalisation.</li>
 *   <li>{@link StoreContentIntelligenceMetadata#collectTagValues(EnrichmentMetadata)} - one {@link LabelSuggestion}
 *       per Hyland action; values surface as tags; description-action suggestions are filtered out defensively.</li>
 * </ul>
 */
@RunWith(MockitoJUnitRunner.class)
public class TestStoreContentIntelligenceMetadata {

    protected StoreContentIntelligenceMetadata consumer;

    @Before
    public void setUp() {
        consumer = new StoreContentIntelligenceMetadata();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // sanitizeTag - low level helper
    // ---------------------------------------------------------------------------------------------------------------

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
    // collectTagValues - description suggestions must be filtered out; values become tags
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldCollectEveryNonDescriptionLabelAsATag() {
        EnrichmentMetadata metadata = buildMetadata(
                suggestion("textClassification", "contract"),
                suggestion("namedEntityText", "Acme Corp", "Jane Doe"));

        Set<String> tags = consumer.collectTagValues(metadata);
        // Sanitised, multi-word -> hyphens, case preserved.
        assertTrue(tags.contains("contract"));
        assertTrue(tags.contains("Acme-Corp"));
        assertTrue(tags.contains("Jane-Doe"));
        assertEquals(3, tags.size());
    }

    @Test
    public void shouldDeduplicateRepeatedLabelValuesAcrossSuggestions() {
        EnrichmentMetadata metadata = buildMetadata(
                suggestion("imageClassification", "landscape"),
                suggestion("namedEntityImage", "landscape"));
        Set<String> tags = consumer.collectTagValues(metadata);
        assertEquals(1, tags.size());
        assertTrue(tags.contains("landscape"));
    }

    @Test
    public void shouldDeduplicateNearDuplicatesWithDifferentHyphenation() {
        // Hyland CI sometimes returns the same concept with different formatting from different actions:
        // e.g. "high-contrast" from one action and "highcontrast" from another.
        // These should collapse to a single tag (the first one encountered is kept).
        EnrichmentMetadata metadata = buildMetadata(
                suggestion("imageClassification", "high-contrast", "very-sharp"),
                suggestion("namedEntityImage", "highcontrast", "verysharp", "photographic-film"),
                suggestion("textClassification", "photographicfilm"));

        Set<String> tags = consumer.collectTagValues(metadata);
        // Should keep only 3 unique tags (first encountered version of each)
        assertEquals(3, tags.size());
        assertTrue(tags.contains("high-contrast"));  // kept, first version
        assertTrue(tags.contains("very-sharp"));     // kept, first version
        assertTrue(tags.contains("photographic-film")); // kept, first version
        // These should NOT be present (near-duplicates filtered out)
        assertTrue(!tags.contains("highcontrast"));
        assertTrue(!tags.contains("verysharp"));
        assertTrue(!tags.contains("photographicfilm"));
    }

    @Test
    public void shouldDeduplicateCaseInsensitively() {
        // Tags differing only in case should also be deduplicated
        EnrichmentMetadata metadata = buildMetadata(
                suggestion("imageClassification", "Landscape"),
                suggestion("namedEntityImage", "landscape", "LANDSCAPE"));

        Set<String> tags = consumer.collectTagValues(metadata);
        assertEquals(1, tags.size());
        assertTrue(tags.contains("Landscape")); // first version kept
    }

    @Test
    public void shouldSkipDescriptionSuggestionsEvenIfPresent() {
        // Defensive: if a description-bearing suggestion ever appears in the labels stream (it shouldn't, the
        // provider strips them) we must NOT tag the document with the full description paragraph.
        EnrichmentMetadata metadata = buildMetadata(
                suggestion("imageDescription", "A very long generated description that should never become a tag"),
                suggestion("textSummary", "An even longer summary paragraph that also must not become a tag"),
                suggestion("imageClassification", "landscape"));

        Set<String> tags = consumer.collectTagValues(metadata);
        assertEquals(Collections.singleton("landscape"), tags);
    }

    @Test
    public void shouldHandleMetadataWithoutLabels() {
        BlobTextFromDocument blobText = buildBlobText();
        EnrichmentMetadata empty = new EnrichmentMetadata.Builder("/classification/imageLabels", "test", blobText)
                .withLabels(Collections.emptyList())
                .build();
        assertTrue(consumer.collectTagValues(empty).isEmpty());
    }

    @Test
    public void shouldIgnoreNullValuesAndBlankNames() {
        // The provider should never produce these, but be defensive.
        List<AIMetadata.Label> mixed = Arrays.asList(
                new AIMetadata.Label("real-value", 1.0F),
                new AIMetadata.Label("", 1.0F),
                new AIMetadata.Label("   ", 1.0F));
        LabelSuggestion s = new LabelSuggestion("namedEntityImage", mixed);
        EnrichmentMetadata metadata = new EnrichmentMetadata.Builder("/classification/imageLabels", "test",
                buildBlobText())
                .withLabels(Collections.singletonList(s))
                .build();

        Set<String> tags = consumer.collectTagValues(metadata);
        assertEquals(Collections.singleton("real-value"), tags);
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

    /** Packs an {@code action -> values...} suggestion in the new label-suggestion shape used by the provider. */
    protected LabelSuggestion suggestion(String action, String... values) {
        List<AIMetadata.Label> labels = new ArrayList<>(values.length);
        for (String v : values) {
            labels.add(new AIMetadata.Label(v, 1.0F));
        }
        return new LabelSuggestion(action, labels);
    }

    /** Wraps any number of {@link LabelSuggestion} into a complete {@link EnrichmentMetadata} ready for assertion. */
    protected EnrichmentMetadata buildMetadata(LabelSuggestion... suggestions) {
        return new EnrichmentMetadata.Builder("/classification/imageLabels", "test", buildBlobText())
                .withLabels(Arrays.asList(suggestions))
                .build();
    }
}
