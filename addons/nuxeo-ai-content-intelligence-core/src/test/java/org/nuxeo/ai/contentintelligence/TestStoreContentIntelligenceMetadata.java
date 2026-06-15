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
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.platform.tag.TagService;

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
        assertEquals("50-percent", consumer.sanitizeTag("% 50 percent"));
    }

    @Test
    public void shouldTrimTrailingHyphenAfterPercentStrip() {
        assertEquals("50-percent", consumer.sanitizeTag("50 percent %"));
    }

    @Test
    public void shouldTrimMultipleEdgeHyphens() {
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
        EnrichmentMetadata metadata = buildMetadata(
                suggestion("namedEntityImage", "%%", "valid-tag"));

        Set<String> tags = consumer.collectTagValues(metadata);
        assertEquals(Collections.singleton("valid-tag"), tags);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // accept - null/blank guard paths
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldHandleAcceptWithNullMetadataWithoutThrowing() {
        consumer.accept(null);
    }

    @Test
    public void shouldHandleAcceptWithNullContext() {
        EnrichmentMetadata metadata = mock(EnrichmentMetadata.class);
        consumer.accept(metadata);
    }

    @Test
    public void shouldHandleAcceptWithNullRepository() throws Exception {
        EnrichmentMetadata metadata = mock(EnrichmentMetadata.class);
        setContext(metadata, new org.nuxeo.ai.metadata.AIMetadata.Context(null, "doc-1", null, null));
        consumer.accept(metadata);
    }

    @Test
    public void shouldHandleAcceptWithBlankDocumentRef() throws Exception {
        EnrichmentMetadata metadata = mock(EnrichmentMetadata.class);
        setContext(metadata, new org.nuxeo.ai.metadata.AIMetadata.Context("repo", "", null, null));
        consumer.accept(metadata);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // applyTags - via overridable getTagService
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldApplyTagsFromCollectedValues() {
        TagService tagService = mock(TagService.class);
        StoreContentIntelligenceMetadata testConsumer = consumerWithTagService(tagService);

        CoreSession session = mock(CoreSession.class);
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("doc-1");

        EnrichmentMetadata metadata = buildMetadata(suggestion("namedEntityImage", "landscape", "sunset"));
        testConsumer.applyTags(session, doc, metadata);

        verify(tagService).tag(session, "doc-1", "landscape");
        verify(tagService).tag(session, "doc-1", "sunset");
    }

    @Test
    public void shouldSkipApplyTagsWhenTagServiceIsNull() {
        StoreContentIntelligenceMetadata testConsumer = consumerWithTagService(null);

        CoreSession session = mock(CoreSession.class);
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getId()).thenReturn("doc-1");

        EnrichmentMetadata metadata = buildMetadata(suggestion("namedEntityImage", "landscape"));
        testConsumer.applyTags(session, doc, metadata);
        verifyNoInteractions(session);
    }

    @Test
    public void shouldSkipApplyTagsWhenNoTagValues() {
        TagService tagService = mock(TagService.class);
        StoreContentIntelligenceMetadata testConsumer = consumerWithTagService(tagService);

        CoreSession session = mock(CoreSession.class);
        DocumentModel doc = mock(DocumentModel.class);

        EnrichmentMetadata metadata = buildMetadata(suggestion("imageDescription", "A long description"));
        testConsumer.applyTags(session, doc, metadata);
        verifyNoInteractions(tagService);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // accept - full transactional path via method override
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void shouldAcceptValidMetadataAndApplyTags() {
        TagService tagService = mock(TagService.class);
        CoreSession mockSession = mock(CoreSession.class);
        DocumentModel mockDoc = mock(DocumentModel.class);
        when(mockDoc.getId()).thenReturn("doc-1");
        when(mockSession.getDocument(new IdRef("doc-1"))).thenReturn(mockDoc);

        StoreContentIntelligenceMetadata testConsumer = consumerWithTransactionalOverride(tagService, mockSession);
        EnrichmentMetadata metadata = buildMetadata(suggestion("namedEntityImage", "landscape"));
        testConsumer.accept(metadata);

        verify(tagService).tag(mockSession, "doc-1", "landscape");
    }

    @Test
    public void shouldHandleDocumentNotFoundInAccept() {
        CoreSession mockSession = mock(CoreSession.class);
        when(mockSession.getDocument(new IdRef("doc-1"))).thenThrow(new DocumentNotFoundException("not found"));

        StoreContentIntelligenceMetadata testConsumer = consumerWithTransactionalOverride(null, mockSession);
        EnrichmentMetadata metadata = buildMetadata(suggestion("namedEntityImage", "landscape"));
        testConsumer.accept(metadata);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------------------------

    protected StoreContentIntelligenceMetadata consumerWithTagService(TagService tagService) {
        return new StoreContentIntelligenceMetadata() {
            @Override
            protected TagService getTagService() {
                return tagService;
            }
        };
    }

    protected StoreContentIntelligenceMetadata consumerWithTransactionalOverride(TagService tagService,
            CoreSession session) {
        return new StoreContentIntelligenceMetadata() {
            @Override
            protected TagService getTagService() {
                return tagService;
            }

            @Override
            protected void acceptInTransaction(EnrichmentMetadata metadata) {
                DocumentModel doc;
                try {
                    doc = session.getDocument(new IdRef(metadata.context.documentRef));
                } catch (DocumentNotFoundException e) {
                    return;
                }
                applyTags(session, doc, metadata);
            }
        };
    }

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

    private void setContext(EnrichmentMetadata metadata, org.nuxeo.ai.metadata.AIMetadata.Context ctx)
            throws Exception {
        java.lang.reflect.Field contextField = org.nuxeo.ai.metadata.AIMetadata.class.getDeclaredField("context");
        contextField.setAccessible(true);
        contextField.set(metadata, ctx);
    }
}
