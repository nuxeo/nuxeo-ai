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


import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.enrichment.EnrichmentTestFeature.blobTestImage;
import static org.nuxeo.ai.model.AIModel.MODEL_NAME;
import static org.nuxeo.ai.pipes.services.JacksonUtil.fromRecord;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toRecord;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentService;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.metadata.SuggestionMetadata;
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
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Tests the overall Model Serving
 */
@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, PlatformFeature.class})
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

    @Test
    public void testServiceConfig() {
        assertNotNull(modelServingService);
        TFRuntimeModel model = (TFRuntimeModel) modelServingService.getModel("xyz");
        assertEquals("dnn", model.getInfo().get(MODEL_NAME));
        assertEquals("1", model.getVersion());
        assertTrue("Model inputs must be set correctly",
                   model.inputNames.containsAll(Arrays.asList("dc:title", "file:content")));
        assertEquals(1, model.getOutputs().size());
        assertEquals("Model outputs must be set correctly",
                     "dc:description", model.getOutputs().iterator().next().getName());
    }

    @Test
    public void testPredict() throws IOException {
        //Create a document
        DocumentModel testDoc = session.createDocumentModel("/", "My Special Doc", "File");
        testDoc.setPropertyValue("dc:title", "My document title");
        testDoc.setPropertyValue("dc:subjects", (Serializable) Arrays.asList("sciences", "art/cinema"));
        testDoc = session.createDocument(testDoc);
        String docId = testDoc.getId();
        session.saveDocument(testDoc);
        txFeature.nextTransaction();

        testDoc.setPropertyValue("file:content", (Serializable) createTestBlob(manager));
        List<SuggestionMetadata> suggestions = modelServingService.predict(testDoc);
        assertEquals(2, suggestions.size());

        //Test serialize results
        SuggestionMetadata andBackAgain = fromRecord(toRecord("t", suggestions.get(0)), SuggestionMetadata.class);
        assertEquals(suggestions.get(0), andBackAgain);
    }

    @Test
    public void testEnrichment() throws IOException {
        BlobTextFromDocument blobTextFromDoc = blobTestImage(manager);
        EnrichmentService service = aiComponent.getEnrichmentService("xyz");
        assertNotNull(service);
        Collection<EnrichmentMetadata> enriched = service.enrich(blobTextFromDoc);
        EnrichmentMetadata metadata = enriched.iterator().next();
        assertEquals(2, metadata.getLabels().size());

        service = aiComponent.getEnrichmentService("customSuggest");
        assertNotNull(service);
        enriched = service.enrich(blobTextFromDoc);
        metadata = enriched.iterator().next();
        assertEquals(2, metadata.getSuggestions().size());

    }

    @Test
    public void testCustomModelEnrichment() throws IOException {
        assertNotNull(aiComponent);
        EnrichmentService service = aiComponent.getEnrichmentService("failingModel");

        BlobTextFromDocument blobTextFromDoc = blobTestImage(manager);
        service = aiComponent.getEnrichmentService("xyz");
        Collection<EnrichmentMetadata> results = service.enrich(blobTextFromDoc);
        assertNotNull("The api must successfully return a result", results);
        assertEquals("There must be 1 result", 1, results.size());
        EnrichmentMetadata metadata = results.iterator().next();
        assertEquals(2, metadata.getLabels().size());
        assertNotNull(metadata.getRawKey());

        TransientStore transientStore = Framework.getService(TransientStoreService.class).getStore("testTransient");
        List<Blob> rawBlobs = transientStore.getBlobs(metadata.getRawKey());
        assertEquals(1, rawBlobs.size());
        String raw = rawBlobs.get(0).getString();
        JsonNode jsonTree = JacksonUtil.MAPPER.readTree(raw);
        assertNotNull(jsonTree);
        assertEquals("The custom model should return results", 1, jsonTree.get("results").size());

        blobTextFromDoc.getBlobs().clear();
        blobTextFromDoc.addProperty("dc:name", "Great product");
        results = service.enrich(blobTextFromDoc);
        assertEquals("There must be 1 result", 1, results.size());
    }

    protected static ManagedBlob blob(Blob blob, String key) {
        return new BlobMetaImpl("test", blob.getMimeType(), key,
                                blob.getDigest(), blob.getEncoding(), blob.getLength()
        );
    }

    /**
     * Create an image blob for testing
     */
    public static ManagedBlob createTestBlob(BlobManager manager) throws IOException {
        BlobProvider blobProvider = manager.getBlobProvider("test");
        Blob blob = Blobs.createBlob(new File(manager.getClass().getResource("/files/plane.jpg").getPath()), "image/jpeg");
        return blob(blob, blobProvider.writeBlob(blob));
    }

}
