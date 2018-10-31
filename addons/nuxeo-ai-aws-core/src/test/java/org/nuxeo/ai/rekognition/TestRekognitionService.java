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
package org.nuxeo.ai.rekognition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.AWS;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentService;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.services.JacksonUtil;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, PlatformFeature.class})
@Deploy({"org.nuxeo.ai.aws.aws-core", "org.nuxeo.ai.aws.aws-core:OSGI-INF/rekognition-test.xml"})
public class TestRekognitionService {

    @Inject
    protected AIComponent aiComponent;

    @Inject
    protected BlobManager manager;

    @Test
    public void testLabelsService() throws IOException {
        AWS.assumeCredentials();
        BlobTextFromDocument blobTextFromDoc = setupBlobTextFromDocument("plane.jpg");

        EnrichmentService service = aiComponent.getEnrichmentService("aws.imageLabels");
        assertNotNull(service);
        Collection<EnrichmentMetadata> metadataCollection = service.enrich(blobTextFromDoc);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata metadata = metadataCollection.iterator().next();
        assertNotNull(metadata);
        assertEquals(blobTextFromDoc.getRepositoryName(), metadata.context.repositoryName);
        assertEquals(blobTextFromDoc.getId(), metadata.context.documentRef);
        assertEquals(1, metadata.context.digests.size());
        assertEquals(blobTextFromDoc.getBlobs().entrySet().iterator().next().getValue().getDigest(),
                     metadata.context.digests.iterator().next());
        assertNotNull(metadata.getLabels());
    }

    @Test
    public void testFaceDetectionService() throws IOException {
        AWS.assumeCredentials();
        BlobTextFromDocument blobTextFromDoc = setupBlobTextFromDocument("creative_commons3.jpg");
        EnrichmentService service = aiComponent.getEnrichmentService("aws.faceDetection");
        assertNotNull(service);
        List<EnrichmentMetadata> metadataCollection = (List<EnrichmentMetadata>) service.enrich(blobTextFromDoc);
        assertEquals(1, metadataCollection.size());
        assertTrue(metadataCollection.get(0).getTags().size() >= 3);
        assertTrue(metadataCollection.get(0).getTags().get(0).features.size() >= 2);
    }

    @Test
    public void testCelebrityDetectionService() throws IOException {
        AWS.assumeCredentials();
        BlobTextFromDocument blobTextFromDoc = setupBlobTextFromDocument("creative_commons2.jpg");
        EnrichmentService service = aiComponent.getEnrichmentService("aws.celebrityDetection");
        assertNotNull(service);
        Collection<EnrichmentMetadata> metadataCollection = service.enrich(blobTextFromDoc);
        assertEquals(1, metadataCollection.size());
        AIMetadata.Tag single = metadataCollection.iterator().next().getTags().iterator().next();
        assertEquals("Steve Ballmer", single.name);
        assertNotNull(single.box);

        blobTextFromDoc = setupBlobTextFromDocument("creative_commons3.jpg");
        metadataCollection = service.enrich(blobTextFromDoc);
        assertEquals(1, metadataCollection.size());

    }

    @Test
    public void testUnsafeImagesService() throws IOException {
        AWS.assumeCredentials();
        BlobTextFromDocument blobTextFromDoc = setupBlobTextFromDocument("creative_commons3.jpg");
        EnrichmentService service = aiComponent.getEnrichmentService("aws.unsafeImages");
        assertNotNull(service);
        Collection<EnrichmentMetadata> metadataCollection = service.enrich(blobTextFromDoc);
        assertEquals(0, metadataCollection.size());

        blobTextFromDoc = setupBlobTextFromDocument("creative_adults-beautiful-blue.jpg");
        metadataCollection = service.enrich(blobTextFromDoc);
        assertEquals(1, metadataCollection.size());
        assertTrue(metadataCollection.iterator().next().getLabels().size() >= 2);
    }

    @Test
    public void testTextDetectionService() throws IOException {
        AWS.assumeCredentials();
        BlobTextFromDocument blobTextFromDoc = setupBlobTextFromDocument("plane.jpg");

        EnrichmentService service = aiComponent.getEnrichmentService("aws.textDetection");
        assertNotNull(service);
        Collection<EnrichmentMetadata> metadataCollection = service.enrich(blobTextFromDoc);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata metadata = metadataCollection.iterator().next();
        assertNotNull(metadata);
        assertFalse(metadata.getTags().isEmpty());
        assertNotNull(metadata.getTags().get(0).box);
        String normalized = JacksonUtil.MAPPER.writeValueAsString(metadata);
        assertNotNull(normalized);

        assertEquals(blobTextFromDoc.getRepositoryName(), metadata.context.repositoryName);
        assertEquals(blobTextFromDoc.getId(), metadata.context.documentRef);
        assertNotNull(metadata.getLabels());
    }

    @NotNull
    protected BlobTextFromDocument setupBlobTextFromDocument(String name) throws IOException {
        BlobProvider blobProvider = manager.getBlobProvider("test");
        Blob blob = Blobs.createBlob(new File(getClass().getResource("/files/" + name).getPath()), "image/jpeg");
        ManagedBlob managedBlob = blob(blob, blobProvider.writeBlob(blob));

        BlobTextFromDocument blobTextFromDoc = new BlobTextFromDocument();
        blobTextFromDoc.setRepositoryName("test");
        blobTextFromDoc.setId(UUID.randomUUID().toString());
        blobTextFromDoc.addBlob("file:content", managedBlob);
        return blobTextFromDoc;
    }

    private ManagedBlob blob(Blob blob, String key) {
        return new BlobMetaImpl("test", blob.getMimeType(), key,
                                key, blob.getEncoding(), blob.getLength()
        );
    }
}
