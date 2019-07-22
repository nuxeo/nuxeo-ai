/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Gethin James
 */
package org.nuxeo.ai.enrichment;

import static java.util.Collections.emptySet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_INPUT_DOCPROP_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_ITEMS;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_MODEL;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_RAW_KEY_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_SCHEMA_NAME;
import static org.nuxeo.ai.AIConstants.NORMALIZED_PROPERTY;
import static org.nuxeo.ai.AIConstants.SUGGESTION_LABEL;
import static org.nuxeo.ai.AIConstants.SUGGESTION_LABELS;
import static org.nuxeo.ai.AIConstants.SUGGESTION_SUGGESTIONS;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.auto.AutoService;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ai.metadata.SuggestionMetadataAdapter;
import org.nuxeo.ai.pipes.services.JacksonUtil;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ai.services.DocMetadataService;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import junit.framework.TestCase;

@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, PlatformFeature.class})
@Deploy({"org.nuxeo.ecm.platform.tag", "org.nuxeo.ai.ai-core", "org.nuxeo.ai.ai-core:OSGI-INF/enrichment-test.xml"})
public class TestDocMetadataService {

    public static final String SERVICE_NAME = "reverse";

    public static final String SOME_TEXT = "You can change me";

    public static final String TEST_PROPERTY = "dc:title";

    @Inject
    protected CoreSession session;

    @Inject
    protected AIComponent aiComponent;

    @Inject
    protected AutoService autoService;

    @Inject
    protected DocMetadataService docMetadataService;

    @Inject
    protected TransactionalFeature txFeature;

    public static EnrichmentMetadata setupTestEnrichmentMetadata(DocumentModel testDoc) {
        List<EnrichmentMetadata.Label> labels = Arrays.asList(new AIMetadata.Label("girl", 0.5f),
                                                              new AIMetadata.Label("boy", 0.4f));
        List<EnrichmentMetadata.Label> labelz = Collections.singletonList(new AIMetadata.Label("cat", 0.9f));
        LabelSuggestion suggestion = new LabelSuggestion("dc:title", labels);
        LabelSuggestion suggestion2 = new LabelSuggestion("dc:format", labelz);

        return new EnrichmentMetadata.Builder("m1", "stest", emptySet(),
                                              testDoc.getRepositoryName(), testDoc.getId(), emptySet())
                .withLabels(Arrays.asList(suggestion, suggestion2))
                .withCreator("bob")
                .build();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSavesEnrichmentData() throws IOException {
        assertNotNull(docMetadataService);
        DocumentModel testDoc = session.createDocumentModel("/", "My Test Doc", "File");
        EnrichmentMetadata metadata = enrichTestDoc(testDoc);
        DocumentModel doc = session.getDocument(testDoc.getRef());
        // Now read the values and check them
        String textReversed = StringUtils.reverse(SOME_TEXT);
        Property classProp = doc.getPropertyObject(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS);
        assertNotNull(classProp);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> classifications = classProp.getValue(List.class);
        assertEquals(2, classifications.size());
        Map<String, Object> classification = classifications.get(0);
        assertEquals(SERVICE_NAME, classification.get(ENRICHMENT_MODEL));
        String[] targetProps = (String[]) classification.get(ENRICHMENT_INPUT_DOCPROP_PROPERTY);
        assertEquals(TEST_PROPERTY, targetProps[0]);
        Blob blob = (Blob) classification.get(ENRICHMENT_RAW_KEY_PROPERTY);
        assertNotNull(blob);
        assertEquals(textReversed, blob.getString());
        assertEquals(textReversed, EnrichmentUtils.getRawBlob(metadata));
        blob = (Blob) classification.get(NORMALIZED_PROPERTY);
        assertEquals(metadata, JacksonUtil.MAPPER.readValue(blob.getString(), EnrichmentMetadata.class));
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) classification.get(SUGGESTION_SUGGESTIONS);
        assertEquals(1, suggestions.size());
        List<Map<String, Object>> labels = (List<Map<String, Object>>) suggestions.get(0).get(SUGGESTION_LABELS);
        assertEquals(1, labels.size());
        assertEquals(textReversed, labels.get(0).get(SUGGESTION_LABEL).toString());

        // Check when there's no metadata to save
        EnrichmentMetadata meta = new EnrichmentMetadata.Builder(Instant.now(), "m1", "test",
                                                                 new AIMetadata.Context(doc.getRepositoryName(), doc
                                                                         .getId(), null, null)).build();
        doc = docMetadataService.saveEnrichment(session, meta);
        txFeature.nextTransaction();
        classProp = doc.getPropertyObject(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS);
        assertNotNull(classProp);
        classifications = classProp.getValue(List.class);
        assertEquals("There is still 3 classifications.", 3, classifications.size());

    }

    @Test
    public void testSavesSuggestions() {
        assertNotNull(docMetadataService);
        DocumentModel testDoc = session.createDocumentModel("/", "My Suggestion Doc", "File");
        testDoc = session.createDocument(testDoc);
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        EnrichmentMetadata suggestionMetadata = setupTestEnrichmentMetadata(testDoc);
        testDoc = docMetadataService.saveEnrichment(session, suggestionMetadata);
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        //Lets save the same data again so we can check we don't duplicate it.
        testDoc = docMetadataService.saveEnrichment(session, suggestionMetadata);
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        Property classProp = testDoc.getPropertyObject(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS);
        assertNotNull(classProp);
        List<Map<String, Object>> suggested = classProp.getValue(List.class);
        assertEquals("There must be 1 suggestion", 1, suggested.size());
        Map<String, Object> suggest = suggested.get(0);
        assertEquals("stest", suggest.get(ENRICHMENT_MODEL));

        SuggestionMetadataAdapter adapted = testDoc.getAdapter(SuggestionMetadataAdapter.class);
        assertTrue(adapted.getModels().contains("stest"));
        assertEquals(2, adapted.getSuggestionsByProperty("dc:title").size());
        assertEquals(1, adapted.getSuggestionsByProperty("dc:format").size());
        assertEquals(3, adapted.getSuggestionsByModel("stest", null)
                               .stream().mapToInt(l -> l.getValues().size()).sum());

        testDoc = docMetadataService.removeSuggestionsForTargetProperty(testDoc, "dc:title");
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        adapted = testDoc.getAdapter(SuggestionMetadataAdapter.class);
        assertTrue(adapted.getSuggestionsByProperty("dc:title").isEmpty());
        assertFalse(adapted.getSuggestionsByProperty("dc:format").isEmpty());

        testDoc = docMetadataService.removeSuggestionsForTargetProperty(testDoc, "dc:format");
        testDoc = session.saveDocument(testDoc);
        txFeature.nextTransaction();
        classProp = testDoc.getPropertyObject(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS);
        adapted = testDoc.getAdapter(SuggestionMetadataAdapter.class);
        assertTrue(adapted.getSuggestionsByProperty("dc:format").isEmpty());
        suggested = classProp.getValue(List.class);
        assertTrue("No longer any suggestions.", suggested.isEmpty());
    }

    @Test
    public void testEnrichedFacetRemoval() {

        // Confirm our test document is enriched
        DocumentModel testDoc = session.createDocumentModel("/", "My Test Enriched document", "File");
        EnrichmentMetadata metadata = enrichTestDoc(testDoc);
        DocumentModel doc = session.getDocument(testDoc.getRef());
        Property classProp = doc.getPropertyObject(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS);
        assertNotNull(classProp);

        // Dirty only 1 of the properties that was enriched, the enrichment will be removed, leaving just 1.
        doc.setPropertyValue(TEST_PROPERTY, "Testing property change");
        autoService.calculateProperties(doc);
        session.saveDocument(doc);
        txFeature.nextTransaction();
        doc = session.getDocument(testDoc.getRef());
        classProp = doc.getPropertyObject(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS);
        // noinspection unchecked
        List<Map<String, Object>> classifications = classProp.getValue(List.class);
        assertEquals("1 of the 2 enrichments must be removed because our test property is dirty", 1,
                     classifications.size());
    }

    /**
     * Enrich our test document, save it and return some metadata
     */
    public EnrichmentMetadata enrichTestDoc(DocumentModel testDoc) {
        testDoc.setPropertyValue(TEST_PROPERTY, "Testing document");
        testDoc = session.createDocument(testDoc);
        txFeature.nextTransaction();

        BlobTextFromDocument blobTextFromDoc = new BlobTextFromDocument(testDoc.getId(), testDoc.getRepositoryName(),
                                                                        testDoc.getParentRef().toString(), testDoc
                                                                                .getType(), testDoc.getFacets());
        blobTextFromDoc.addProperty(TEST_PROPERTY, SOME_TEXT);
        EnrichmentService service = aiComponent.getEnrichmentService(SERVICE_NAME);
        Collection<EnrichmentMetadata> results = service.enrich(blobTextFromDoc);
        TestCase.assertEquals(2, results.size());
        EnrichmentMetadata[] metaResults = results.toArray(new EnrichmentMetadata[0]);
        EnrichmentMetadata metadata = metaResults[0];
        DocumentModel docToSave = docMetadataService.saveEnrichment(session, metadata);
        session.saveDocument(docToSave);
        docToSave = docMetadataService.saveEnrichment(session, metaResults[1]);
        session.saveDocument(docToSave);
        txFeature.nextTransaction();
        return metadata;
    }
}
