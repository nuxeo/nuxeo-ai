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


import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.enrichment.EnrichmentTestFeature.blobTestImage;
import static org.nuxeo.ai.model.AIModel.MODEL_NAME;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.AI_BLOB_MAX_SIZE_CONF_VAR;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.AI_BLOB_MAX_SIZE_VALUE;
import static org.nuxeo.ai.pipes.services.JacksonUtil.fromRecord;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toRecord;
import static org.nuxeo.ecm.core.io.registry.context.RenderingContext.CtxBuilder.enrichDoc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentProvider;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.model.ModelProperty;
import org.nuxeo.ai.pipes.services.JacksonUtil;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.io.CoreIOFeature;
import org.nuxeo.ecm.core.io.marshallers.json.document.DocumentModelJsonWriter;
import org.nuxeo.ecm.core.io.registry.MarshallerRegistry;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

/**
 * Tests the overall Model Serving
 */
@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, PlatformFeature.class, CoreIOFeature.class})
@Deploy("org.nuxeo.ai.nuxeo-jwt-authenticator-core")
@Deploy("org.nuxeo.ai.ai-core")
@Deploy("org.nuxeo.ai.ai-model")
@Deploy("org.nuxeo.ai.ai-model:OSGI-INF/model-serving-test.xml")
public class TestModelServing {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(5089);

    @Inject
    protected ModelServingService modelServingService;

    @Inject
    protected CoreSession session;

    @Inject
    protected BlobManager manager;

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected AIComponent aiComponent;

    @Inject
    protected MarshallerRegistry registry;

    @Test
    public void testServiceConfig() {
        assertNotNull(modelServingService);
        TFRuntimeModel model = (TFRuntimeModel) modelServingService.getModel("xyz");
        assertEquals("mockTestModel", model.getInfo().get(MODEL_NAME));
        assertEquals("1", model.getVersion());
        assertTrue("Model inputs must be set correctly",
                   model.inputNames.containsAll(Arrays.asList("dc:title", "file:content")));
        assertEquals(1, model.getOutputs().size());
        assertEquals("Model outputs must be set correctly",
                     "dc:description", model.getOutputs().iterator().next().getName());
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void testPredict() throws IOException {
        //Create a document
        DocumentModel testDoc = session.createDocumentModel("/", "My Special Doc", "FileRefDoc");
        testDoc.setPropertyValue("dc:title", "My document title");
        testDoc.setPropertyValue("dc:subjects", (Serializable) Arrays.asList("sciences", "art/cinema"));
        testDoc = session.createDocument(testDoc);
        String docId = testDoc.getId();
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        testDoc.setPropertyValue("file:content", (Serializable) createTestBlob(manager));
        List<EnrichmentMetadata> suggestions = modelServingService.predict(testDoc);
        assertEquals(2, suggestions.size());

        //Test serialize results
        EnrichmentMetadata andBackAgain = fromRecord(toRecord("t", suggestions.get(0)), EnrichmentMetadata.class);
        assertEquals(suggestions.get(0), andBackAgain);
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void testEnrichment() throws IOException {
        BlobTextFromDocument blobTextFromDoc = blobTestImage(manager);
        EnrichmentProvider service = aiComponent.getEnrichmentProvider("xyz");
        assertNotNull(service);
        Collection<EnrichmentMetadata> enriched = service.enrich(blobTextFromDoc);
        assertTrue("We didn't specify all the params so it should not be enriched.", enriched.isEmpty());

        blobTextFromDoc.addProperty("dc:title", "My test doc");
        blobTextFromDoc.addProperty("ecm:mixinType", "Versionable | Downloadable");
        enriched = service.enrich(blobTextFromDoc);
        EnrichmentMetadata metadata = enriched.iterator().next();
        assertEquals(7, metadata.getLabels().stream().mapToInt(l -> l.getValues().size()).sum());

        service = aiComponent.getEnrichmentProvider("customSuggest");
        assertNotNull(service);
        Collection<EnrichmentMetadata> suggest = service.enrich(blobTextFromDoc);
        EnrichmentMetadata enrichmentMetadata = suggest.iterator().next();
        assertEquals(4, enrichmentMetadata.getLabels().size());
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void iCanConfigConversionMaxSize() throws IOException {
        // Init conf var to very small blob size
        Framework.getProperties().put(AI_BLOB_MAX_SIZE_CONF_VAR, "300");

        // Init Enrichment
        BlobTextFromDocument blobTextFromDoc = blobTestImage(manager);
        EnrichmentProvider service = aiComponent.getEnrichmentProvider("xyz");
        assertNotNull(service);
        blobTextFromDoc.addProperty("dc:title", "My test doc");
        blobTextFromDoc.addProperty("ecm:mixinType", "Versionable | Downloadable");

        // Should return none as max size has been reached
        Collection<EnrichmentMetadata> enriched = service.enrich(blobTextFromDoc);
        assertThat(enriched).isEmpty();

        // Put back conf var to very small blob size
        Framework.getProperties().put(AI_BLOB_MAX_SIZE_CONF_VAR, AI_BLOB_MAX_SIZE_VALUE);
        Collection<EnrichmentMetadata> suggest = service.enrich(blobTextFromDoc);
        EnrichmentMetadata enrichmentMetadata = suggest.iterator().next();
        assertEquals(4, enrichmentMetadata.getLabels().size());
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void testCustomModelEnrichment() throws IOException {
        assertNotNull(aiComponent);
        BlobTextFromDocument blobTextFromDoc = blobTestImage(manager);
        blobTextFromDoc.addProperty("dc:title", "My Custom doc");
        blobTextFromDoc.addProperty("ecm:mixinType", "Downloadable");
        EnrichmentProvider service = aiComponent.getEnrichmentProvider("xyz");
        Collection<EnrichmentMetadata> results = service.enrich(blobTextFromDoc);
        assertNotNull("The api must successfully return a result", results);
        assertEquals("There must be 1 result", 1, results.size());
        EnrichmentMetadata metadata = results.iterator().next();
        assertEquals(7, metadata.getLabels().stream().mapToInt(l -> l.getValues().size()).sum());
        assertNotNull(metadata.getRawKey());

        TransientStore transientStore = Framework.getService(TransientStoreService.class).getStore("testTransient");
        List<Blob> rawBlobs = transientStore.getBlobs(metadata.getRawKey());
        assertEquals(1, rawBlobs.size());
        String raw = rawBlobs.get(0).getString();
        JsonNode jsonTree = JacksonUtil.MAPPER.readTree(raw);
        assertNotNull(jsonTree);
        assertEquals("The custom model should return results", 1, jsonTree.get("results").size());
    }

    protected static ManagedBlob blob(Blob blob, String key) {
        return new BlobMetaImpl("test", blob.getMimeType(), key,
                                blob.getDigest(), blob.getEncoding(), blob.getLength()
        );
    }

    @Test
    public void testModelListing() {
        Collection<ModelDescriptor> models = modelServingService.listModels();
        assertEquals(3, models.size());

        DocumentModel testDoc = session.createDocumentModel("/", "My Model Doc", "FileRefDoc");
        Set<ModelProperty> inputs = modelServingService.getInputs(testDoc);
        assertEquals(4, inputs.size());

        testDoc = session.createDocumentModel("/", "My note Doc", "Note");
        inputs = modelServingService.getInputs(testDoc);
        assertEquals(1, inputs.size());

    }

    /**
     * Create an image blob for testing
     */
    public static ManagedBlob createTestBlob(BlobManager manager) throws IOException {
        BlobProvider blobProvider = manager.getBlobProvider("test");
        Blob blob = Blobs.createBlob(new File(manager.getClass().getResource("/files/plane.jpg").getPath()), "image/jpeg");
        return blob(blob, blobProvider.writeBlob(blob));
    }

    @Test
    public void testDocumentEnricher() throws IOException {
        DocumentModelJsonWriter writer = registry
                .getInstance(enrichDoc(ModelJsonEnricher.NAME).get(), DocumentModelJsonWriter.class);
        DocumentModel testDoc = session.createDocumentModel("/", "My Test Doc", "FileRefDoc");
        testDoc = session.createDocument(testDoc);
        session.saveDocument(testDoc);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writer.write(testDoc, DocumentModel.class, DocumentModel.class, APPLICATION_JSON_TYPE, baos);
        String result = new String(baos.toByteArray());
        assertTrue(result.contains("\"aiModels\":{\"inputs\":"));
        assertTrue(result.contains("{\"name\":\"dc:title\",\"type\":\"txt\"}"));
        assertTrue(result.contains("{\"name\":\"file:content\",\"type\":\"img\"}"));
    }

}
