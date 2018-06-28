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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentService;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.stream.pipes.services.JacksonUtil;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, PlatformFeature.class})
@Deploy({"org.nuxeo.ai.aws.aws-core"})
public class TestRekognitionService {

    @Inject
    protected AIComponent aiComponent;

    @Inject
    protected BlobManager manager;

    @Test
    public void testLabelsService() throws IOException {

        BlobTextStream blobTextStream = setupBlobTextStream("plane.jpg");

        EnrichmentService service = aiComponent.getEnrichmentService("aws.imageLabels");
        assertNotNull(service);
        Collection<EnrichmentMetadata> metadataCollection = service.enrich(blobTextStream);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata metadata = metadataCollection.iterator().next();
        assertNotNull(metadata);
        assertEquals(blobTextStream.getRepositoryName(), metadata.context.repositoryName);
        assertEquals(blobTextStream.getId(), metadata.context.documentRef);
        assertEquals(blobTextStream.getBlob().getDigest(), metadata.context.blobDigest);
        assertNotNull(metadata.getLabels());
    }

    @Test
    public void testFaceDetectionService() throws IOException {
        BlobTextStream blobTextStream = setupBlobTextStream("creative_commons3.jpg");
        EnrichmentService service = aiComponent.getEnrichmentService("aws.faceDetection");
        assertNotNull(service);
        List<EnrichmentMetadata> metadataCollection = (List<EnrichmentMetadata>) service.enrich(blobTextStream);
        assertEquals(1, metadataCollection.size());
        assertTrue(metadataCollection.get(0).getTags().size() >= 3);
        assertTrue(metadataCollection.get(0).getTags().get(0).features.size() >= 2);
    }

    @Test
    public void testCelebrityDetectionService() throws IOException {
        BlobTextStream blobTextStream = setupBlobTextStream("creative_commons2.jpg");
        EnrichmentService service = aiComponent.getEnrichmentService("aws.celebrityDetection");
        assertNotNull(service);
        Collection<EnrichmentMetadata> metadataCollection = service.enrich(blobTextStream);
        assertEquals(1, metadataCollection.size());
        AIMetadata.Tag single = metadataCollection.iterator().next().getTags().iterator().next();
        assertEquals("Steve Ballmer", single.name);
        assertNotNull(single.box);

        blobTextStream = setupBlobTextStream("creative_commons3.jpg");
        metadataCollection = service.enrich(blobTextStream);
        assertEquals(1, metadataCollection.size());

    }

    @Test
    public void testTextDetectionService() throws IOException {

        BlobTextStream blobTextStream = setupBlobTextStream("plane.jpg");

        EnrichmentService service = aiComponent.getEnrichmentService("aws.textDetection");
        assertNotNull(service);
        Collection<EnrichmentMetadata> metadataCollection = service.enrich(blobTextStream);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata metadata = metadataCollection.iterator().next();
        assertNotNull(metadata);
        assertFalse(metadata.getTags().isEmpty());
        assertNotNull(metadata.getTags().get(0).box);
        String normalized = JacksonUtil.MAPPER.writeValueAsString(metadata);
        assertNotNull(normalized);

        assertEquals(blobTextStream.getRepositoryName(), metadata.context.repositoryName);
        assertEquals(blobTextStream.getId(), metadata.context.documentRef);
        assertEquals(blobTextStream.getBlob().getDigest(), metadata.context.blobDigest);
        assertNotNull(metadata.getLabels());
    }

    @NotNull
    protected BlobTextStream setupBlobTextStream(String name) throws IOException {
        BlobProvider blobProvider = manager.getBlobProvider("test");
        Blob blob = Blobs.createBlob(new File(getClass().getResource("/files/" + name).getPath()), "image/jpeg");
        ManagedBlob plane = blob(blob, blobProvider.writeBlob(blob));

        BlobTextStream blobTextStream = new BlobTextStream();
        blobTextStream.setRepositoryName("test");
        blobTextStream.setId("docId");
        blobTextStream.setBlob(plane);
        return blobTextStream;
    }

    private ManagedBlob blob(Blob blob, String key) {
        return new BlobMetaImpl("test", blob.getMimeType(), key,
                                blob.getDigest(), blob.getEncoding(), blob.getLength()
        );
    }
}
