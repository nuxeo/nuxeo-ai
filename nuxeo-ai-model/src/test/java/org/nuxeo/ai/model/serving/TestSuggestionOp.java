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
package org.nuxeo.ai.model.serving;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.common.utils.Path;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import javax.inject.Inject;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.model.serving.TestModelServing.createTestBlob;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

/**
 * Tests the Suggestion Operation.
 */
@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, PlatformFeature.class, AutomationFeature.class})
@Deploy({"org.nuxeo.ai.ai-core", "org.nuxeo.ai.ai-model", "org.nuxeo.ai.ai-model:OSGI-INF/model-serving-test.xml"})
public class TestSuggestionOp {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(5089);

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Inject
    protected BlobManager manager;

    @Test(expected = OperationException.class)
    public void shouldThrowExceptionWhenInputIsNull() throws OperationException {
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(null);
        automationService.run(ctx, SuggestionOp.ID);
    }

    @Test(expected = DocumentNotFoundException.class)
    public void shouldThrowExceptionWhenDocRefIsInvalid() throws OperationException {
        OperationContext ctx = new OperationContext(session);
        DocumentRef docRef = new IdRef("invalidId");
        ctx.setInput(docRef);
        automationService.run(ctx, SuggestionOp.ID);
    }

    @Test
    public void shouldNotPersistDocumentPropertiesWhenTheyAreProvided() throws OperationException {
        DocumentModel document = session.createDocumentModel("/", "My Doc", "FileRefDoc");
        document.setPropertyValue("dc:title", "my document");
        document.setPropertyValue("dc:description", "some description");
        document = session.createDocument(document);
        session.save();

        DocumentModel updatedDocument = session.getDocument(document.getRef());
        updatedDocument.setPropertyValue("dc:description", "updated description here");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("updatedDocument", updatedDocument);

        OperationContext ctx = new OperationContext(session);
        ctx.setInput(document);
        ctx.putChainParameters(parameters);
        automationService.run(ctx, SuggestionOp.ID); // Result is not the aim of this test

        DocumentModel returnedDocument = session.getDocument(document.getRef());
        assertEquals("some description", returnedDocument.getPropertyValue("dc:description"));
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void shouldCall() throws OperationException, IOException {

        DocumentModel referencedDoc = new DocumentModelImpl("File", "123456", new Path("referenced doc"), null,
                null, null, null, null, false, null, null, null);
        session.importDocuments(Collections.singletonList(referencedDoc));

        String title = "My document suggestion";
        DocumentModel testDoc = session.createDocumentModel("/", "My Doc", "FileRefDoc");
        testDoc.setPropertyValue("dc:title", title);
        testDoc.setPropertyValue("file:content", (Serializable) createTestBlob(manager));
        testDoc = session.createDocument(testDoc);
        session.save();

        Map<String, Object> resolveParams = new HashMap<>();
        resolveParams.put("references", true);
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(testDoc);
        OperationChain chain = new OperationChain("testSuggestChain2");
        chain.add(SuggestionOp.ID).from(resolveParams);
        assertSuggestionList((JSONBlob) automationService.run(ctx, chain));

        ctx = new OperationContext(session);
        ctx.setInput(testDoc.getRef());
        chain = new OperationChain("testSuggestChain2b");
        chain.add(SuggestionOp.ID).from(resolveParams);
        assertSuggestionList((JSONBlob) automationService.run(ctx, chain));

        ctx = new OperationContext(session);
        ctx.setInput(testDoc);
        chain = new OperationChain("testSuggestChainWithResolve");
        chain.add(SuggestionOp.ID);
        assertSimpleSuggestionList((JSONBlob) automationService.run(ctx, chain));

        testDoc = session.createDocumentModel("/", "My Doc", "Note");
        testDoc = session.createDocument(testDoc);
        session.save();
        ctx = new OperationContext(session);
        ctx.setInput(testDoc);
        chain = new OperationChain("testSuggestChain3");
        chain.add(SuggestionOp.ID);
        // should fail because of the Note document type
        assertEmptyList((JSONBlob) automationService.run(ctx, chain));
    }

    protected void assertSuggestionList(JSONBlob blob) throws IOException {
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
                    assertTrue(entityType.contains("document"));
                    break;
                case "dc:creator":
                    assertTrue(entityType.contains("user"));
                    break;
                case "dc:nature":
                    assertTrue(entityType.contains("directoryEntry"));
                    break;
                case "dc:subjects":
                    assertTrue(entityType.contains("directoryEntry"));
            }
        }
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

    protected void assertEmptyList(JSONBlob blob) {
        assertEquals(SuggestionOp.EMPTY_JSON_LIST, blob.getString());
    }
}
