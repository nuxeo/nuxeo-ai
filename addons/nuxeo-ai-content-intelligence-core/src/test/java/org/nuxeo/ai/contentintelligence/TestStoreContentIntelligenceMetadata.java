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
 * Unit tests for {@link StoreContentIntelligenceMetadata}. The consumer is strictly a tag-writer: long-form
 * description / summary text is delegated to {@link ContentIntelligenceDescriptionListener} and must never reach
 * this code path through the labels stream.
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
        assertEquals("New-York-City", consumer.sanitizeTag("New York City"));
        assertEquals("Jane-Doe", consumer.sanitizeTag(" Jane   Doe "));
    }

    @Test
    public void shouldStripUnsafeCharactersFromTags() {
        assertEquals("acmecorp", consumer.sanitizeTag("acme/corp"));
        assertEquals("ODonnell", consumer.sanitizeTag("O'Donnell"));
        assertEquals("50-percent", consumer.sanitizeTag("50 %percent"));
        assertEquals("back-slash", consumer.sanitizeTag("back\\ slash"));
    }

    @Test
    public void shouldTrimLeadingHyphenAfterPercentStrip() {
        // "% 50 percent" -> trim -> "% 50 percent" -> WHITESPACE -> "%-50-percent"
        // -> TAG_SANITIZER strips % -> "-50-percent" -> EDGE_HYPHENS strips leading hyphen -> "50-percent".
        assertEquals("50-percent", consumer.sanitizeTag("% 50 percent"));
    }

    @Test
    public void shouldTrimTrailingHyphenAfterPercentStrip() {
        // Symmetric case for the trailing edge.
        assertEquals("50-percent", consumer.sanitizeTag("50 percent %"));
    }

    @Test
    public void shouldTrimMultipleEdgeHyphens() {
        // Path-style inputs can produce multiple leading/trailing hyphens after sanitisation.
        assertEquals("alpha-beta", consumer.sanitizeTag("// alpha beta //"));
    }

    @Test
    public void shouldCollapseToNullWhenAllCharactersAreUnsafe() {
        assertNull(consumer.sanitizeTag("//%%''"));
        assertNull(consumer.sanitizeTag("---"));
    }

    @Test
    public void shouldReturnNullForBlankSanitizerInput() {
        assertNull(consumer.sanitizeTag(null));
        assertNull(consumer.sanitizeTag(""));
        assertNull(consumer.sanitizeTag("   "));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // collectTagValues
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldCollectEveryNonDescriptionLabelAsATag() {
        EnrichmentMetadata metadata = buildMetadata(
                suggestion("namedEntityImage", "contract"),
                suggestion("namedEntityText", "Acme Corp", "Jane Doe"));

        Set<String> tags = consumer.collectTagValues(metadata);
        assertTrue(tags.contains("contract"));
        assertTrue(tags.contains("Acme-Corp"));
        assertTrue(tags.contains("Jane-Doe"));
        assertEquals(3, tags.size());
    }

    @Test
    public void shouldDeduplicateRepeatedLabelValuesAcrossSuggestions() {
        EnrichmentMetadata metadata = buildMetadata(
                suggestion("namedEntityText", "landscape"),
                suggestion("namedEntityImage", "landscape"));
        Set<String> tags = consumer.collectTagValues(metadata);
        assertEquals(1, tags.size());
        assertTrue(tags.contains("landscape"));
    }

    @Test
    public void shouldDeduplicateNearDuplicatesWithDifferentHyphenation() {
        EnrichmentMetadata metadata = buildMetadata(
                suggestion("imageMetadataGeneration", "high-contrast", "very-sharp"),
                suggestion("namedEntityImage", "highcontrast", "verysharp", "photographic-film"),
                suggestion("namedEntityText", "photographicfilm"));

        Set<String> tags = consumer.collectTagValues(metadata);
        assertEquals(3, tags.size());
        assertTrue(tags.contains("high-contrast"));
        assertTrue(tags.contains("very-sharp"));
        assertTrue(tags.contains("photographic-film"));
        assertFalse(tags.contains("highcontrast"));
        assertFalse(tags.contains("verysharp"));
        assertFalse(tags.contains("photographicfilm"));
    }

    @Test
    public void shouldDeduplicateCaseInsensitively() {
        EnrichmentMetadata metadata = buildMetadata(
                suggestion("namedEntityText", "Landscape"),
                suggestion("namedEntityImage", "landscape", "LANDSCAPE"));

        Set<String> tags = consumer.collectTagValues(metadata);
        assertEquals(1, tags.size());
        assertTrue(tags.contains("Landscape"));
    }

    @Test
    public void shouldSkipDescriptionSuggestionsEvenIfPresent() {
        EnrichmentMetadata metadata = buildMetadata(
                suggestion("imageDescription", "A very long generated description that should never become a tag"),
                suggestion("textSummary", "An even longer summary paragraph that also must not become a tag"),
                suggestion("namedEntityImage", "landscape"));

        Set<String> tags = consumer.collectTagValues(metadata);
        assertEquals(Collections.singleton("landscape"), tags);
    }

    @Test
    public void shouldReturnEmptySetForMetadataWithoutLabels() {
        BlobTextFromDocument blobText = buildBlobText();
        EnrichmentMetadata empty = new EnrichmentMetadata.Builder("/classification/imageLabels", "test", blobText)
                .withLabels(Collections.emptyList())
                .build();
        assertTrue(consumer.collectTagValues(empty).isEmpty());
    }

    @Test
    public void shouldIgnoreNullValuesAndBlankNames() {
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

    @Test
    public void shouldDropTagThatSanitizesToBlank() {
        // "%%" -> "" -> dropped.
        EnrichmentMetadata metadata = buildMetadata(
                suggestion("namedEntityImage", "%%", "valid-tag"));

        Set<String> tags = consumer.collectTagValues(metadata);
        assertEquals(Collections.singleton("valid-tag"), tags);
    }

    @Test
    public void shouldHandleAcceptWithNullMetadataWithoutThrowing() {
        // Defensive: a null metadata must not propagate as an exception; the consumer just logs and returns.
        consumer.accept(null);
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

    protected LabelSuggestion suggestion(String action, String... values) {
        List<AIMetadata.Label> labels = new ArrayList<>(values.length);
        for (String v : values) {
            labels.add(new AIMetadata.Label(v, 1.0F));
        }
        return new LabelSuggestion(action, labels);
    }

    protected EnrichmentMetadata buildMetadata(LabelSuggestion... suggestions) {
        return new EnrichmentMetadata.Builder("/classification/imageLabels", "test", buildBlobText())
                .withLabels(Arrays.asList(suggestions))
                .build();
    }
}
