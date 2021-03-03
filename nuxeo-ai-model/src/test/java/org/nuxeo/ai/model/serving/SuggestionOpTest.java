/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuno Cunha <ncunha@nuxeo.com>
 */
package org.nuxeo.ai.model.serving;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.impl.SimpleDocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.mockito.MockitoFeature;
import org.nuxeo.runtime.mockito.RuntimeService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Unit Tests the Suggestion Operation.
 */
@RunWith(FeaturesRunner.class)
@Features({ EnrichmentTestFeature.class, PlatformFeature.class, AutomationFeature.class, MockitoFeature.class })
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
@Deploy({ "org.nuxeo.ai.ai-core", "org.nuxeo.ai.ai-model", "org.nuxeo.ai.ai-model:OSGI-INF/model-serving-test.xml" })
public class SuggestionOpTest {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Mock
    @RuntimeService
    protected ModelServingService modelServingService;

    protected OperationContext ctx;

    protected DocumentModel documentModel;

    @Before
    public void setUp() {
        documentModel = session.createDocumentModel("/", "My Doc", "FileRefDoc");
        documentModel.setPropertyValue("dc:title", "my document");
        documentModel.setPropertyValue("dc:description", "some description");
        documentModel = session.createDocument(documentModel);
        session.save();

        ctx = new OperationContext(session);
        ctx.setInput(documentModel);

        when(modelServingService.predict(eq(documentModel))).thenReturn(getSamplePrediction());
    }

    @After
    public void tearDown() {
        ctx.close();
    }

    @Test(expected = OperationException.class)
    public void shouldThrowExceptionWhenInputIsNull() throws OperationException {
        ctx.setInput(null);
        automationService.run(ctx, SuggestionOp.ID);
    }

    @Test(expected = DocumentNotFoundException.class)
    public void shouldThrowExceptionWhenDocRefIsInvalid() throws OperationException {
        ctx.setInput(new IdRef("invalidId"));
        automationService.run(ctx, SuggestionOp.ID);
    }

    @Test
    public void shouldNotPersistDocumentPropertiesWhenTheyAreProvided() throws OperationException {
        DocumentModel updatedDocument = session.getDocument(documentModel.getRef());
        updatedDocument.setPropertyValue("dc:description", "updated description here");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("updatedDocument", updatedDocument);

        automationService.run(ctx, SuggestionOp.ID, parameters);

        DocumentModel returnedDocument = session.getDocument(documentModel.getRef());
        assertEquals("some description", returnedDocument.getPropertyValue("dc:description"));
    }

    @Test
    public void shouldCallPredictionServiceWithMergedPropertiesWhenDocAlreadyExists() throws OperationException {
        DocumentModel updatedDocument = session.getDocument(documentModel.getRef());
        updatedDocument.setPropertyValue("dc:description", "updated description here");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("updatedDocument", updatedDocument);

        automationService.run(ctx, SuggestionOp.ID, parameters);

        ArgumentCaptor<DocumentModel> argument = ArgumentCaptor.forClass(DocumentModel.class);
        verify(modelServingService).predict(argument.capture());
        assertEquals("my document", argument.getValue().getPropertyValue("dc:title"));
        assertEquals("updated description here", argument.getValue().getPropertyValue("dc:description"));
    }

    @Test
    public void shouldResolvePropertiesFromSimpleDoc() throws OperationException {
        SimpleDocumentModel simple = SimpleDocumentModel.ofSchemas("uid", "file", "common", "files", "dublincore",
                "relatedtext");
        simple.setPropertyValue("dc:title", "Been updated");
        simple.setPropertyValue("dc:description", "Coming from the update");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("updatedDocument", simple);

        DocumentModel fileDoc = session.createDocumentModel("/", "Test File", "File");
        fileDoc.setPropertyValue("dc:title", "Test File");
        fileDoc = session.createDocument(fileDoc);

        OperationContext opCtx = new OperationContext(session);
        opCtx.setInput(fileDoc);
        automationService.run(opCtx, SuggestionOp.ID, parameters);

        ArgumentCaptor<DocumentModel> argument = ArgumentCaptor.forClass(DocumentModel.class);
        verify(modelServingService).predict(argument.capture());
        assertEquals("Been updated", argument.getValue().getPropertyValue("dc:title"));
        assertEquals("Coming from the update", argument.getValue().getPropertyValue("dc:description"));
    }

    @Test
    public void shouldReturnEmptyJsonListWhenSuggestionsAreNull() throws OperationException {
        DocumentModel doc = Mockito.mock(DocumentModel.class);
        when(modelServingService.predict(eq(doc))).thenReturn(null);

        OperationContext ctx = new OperationContext(session);
        ctx.setInput(doc);
        assertEmptyList((JSONBlob) automationService.run(ctx, SuggestionOp.ID));
        verify(modelServingService).predict(eq(doc));
    }

    @Test
    public void shouldReturnEmptyJsonListWhenSuggestionsAreEmpty() throws OperationException {
        DocumentModel doc = Mockito.mock(DocumentModel.class);
        when(modelServingService.predict(eq(doc))).thenReturn(Collections.emptyList());

        OperationContext ctx = new OperationContext(session);
        ctx.setInput(doc);
        assertEmptyList((JSONBlob) automationService.run(ctx, SuggestionOp.ID));
        verify(modelServingService).predict(eq(doc));
    }

    @Test
    public void shouldReturnSimpleSuggestionListWhenNoReferencesParamIsPassed() throws OperationException, IOException {
        assertSimpleSuggestionList((JSONBlob) automationService.run(ctx, SuggestionOp.ID));
    }

    @Test
    public void shouldReturnResolvedSuggestionListWhenReferencesParamIsTrue() throws OperationException, IOException {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("references", true);

        assertResolvedSuggestionList((JSONBlob) automationService.run(ctx, SuggestionOp.ID, parameters));
    }

    protected List<EnrichmentMetadata> getSamplePrediction() {
        EnrichmentMetadata.Builder builder = new EnrichmentMetadata.Builder("/prediction/custommodel", "xyz",
                Collections.emptySet(), "repoName", "docRef", Collections.emptySet());
        builder.withLabels(Arrays.asList(new LabelSuggestion("dr:docIdOnlyRef",
                        Arrays.asList(new AIMetadata.Label("123456", 0.8528175f),
                                new AIMetadata.Label("not_finding_this_one", 0.864372f))), new LabelSuggestion("dc:creator",
                        Arrays.asList(new AIMetadata.Label("me", 0.9528175f),
                                new AIMetadata.Label("Administrator", 0.83437204f))), new LabelSuggestion("dc:nature",
                        Collections.singletonList(new AIMetadata.Label("report", 0.83437204f))),
                new LabelSuggestion("dc:subjects", Arrays.asList(new AIMetadata.Label("NO_MATCH", 0.8650408f),
                        new AIMetadata.Label("music", 0.83437204f)))));

        return Collections.singletonList(builder.build());
    }

    protected void assertEmptyList(JSONBlob blob) {
        assertEquals(SuggestionOp.EMPTY_JSON_LIST, blob.getString());
    }

    protected void assertSimpleSuggestionList(JSONBlob blob) throws IOException {
        JsonNode jsonTree = MAPPER.readTree(blob.getString());
        JsonNode suggestions = jsonTree.get(0).get("suggestions");
        assertEquals(4, suggestions.size());
        for (JsonNode suggestion : suggestions) {
            List<JsonNode> asProperty = suggestion.get("values").findValues("value");
            assertTrue(asProperty.stream().allMatch(n -> n instanceof TextNode));
        }
    }

    protected void assertResolvedSuggestionList(JSONBlob blob) throws IOException {
        JsonNode jsonTree = MAPPER.readTree(blob.getString());
        JsonNode suggestions = jsonTree.get(0).get("suggestions");
        assertEquals(4, suggestions.size());
        for (JsonNode suggestion : suggestions) {
            List<JsonNode> asProperty = suggestion.get("values").findValues("value");
            Set<String> entityType = asProperty.stream()
                                               .map(jsonNode -> jsonNode.get("entity-type"))
                                               .filter(Objects::nonNull)
                                               .map(JsonNode::asText)
                                               .collect(Collectors.toSet());
            switch (suggestion.get("property").asText()) {
            case "dr:docIdOnlyRef":
                assertFalse(entityType.contains("document"));
                break;
            case "dc:creator":
                assertTrue(entityType.contains("user"));
                break;
            default:
                assertTrue(entityType.contains("directoryEntry"));
                break;
            }
        }
    }
}
