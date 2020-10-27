/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo
 */
package org.nuxeo.ai.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.nuxeo.ai.gcp.AIGoogleServiceImpl.GCP_JSON_FILE;
import static org.nuxeo.ai.gcp.AIGoogleServiceImpl.GOOGLE_APPLICATION_CREDENTIALS;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.IMAGE_TYPE;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.inject.Inject;

import org.jetbrains.annotations.NotNull;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentProvider;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.protobuf.ByteString;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class, EnrichmentTestFeature.class })
@Deploy("org.nuxeo.ai.gcp.gcp-core")
public class TestService {

    @Inject
    protected AIGoogleService aiGoogleService;

    @Inject
    protected AIComponent aic;

    @Inject
    protected BlobManager manager;

    @Inject
    protected CoreSession session;

    @Before
    public void assumeCredentials() {
        Assume.assumeFalse("GCP tests deactivation is set",
                Framework.getProperty(GOOGLE_APPLICATION_CREDENTIALS, GCP_JSON_FILE) == null);
    }

    @Test
    public void iCanTestGoogleService() throws IOException {
        try (ImageAnnotatorClient vision = aiGoogleService.getOrCreateClient()) {
            File imageFile = FileUtils.getResourceFileFromContext("files/wakeupcat.jpg");
            byte[] data = Files.readAllBytes(imageFile.toPath());
            ByteString imgBytes = ByteString.copyFrom(data);

            List<AnnotateImageRequest> requests = new ArrayList<>();
            Image img = Image.newBuilder().setContent(imgBytes).build();
            Feature feat = Feature.newBuilder().setType(Feature.Type.LABEL_DETECTION).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();
            requests.add(request);

            BatchAnnotateImagesResponse response = vision.batchAnnotateImages(requests);
            List<AnnotateImageResponse> responses = response.getResponsesList();
            for (AnnotateImageResponse res : responses) {
                if (res.hasError()) {
                    fail();
                }
                assertThat(res.getLabelAnnotationsList()).hasSize(10);
                for (EntityAnnotation annotation : res.getLabelAnnotationsList()) {
                    annotation.getAllFields().forEach((k, v) -> System.out.format("%s : %s%n", k, v.toString()));
                }
            }
        }
    }

    @Test
    public void shouldCallGCPLabelProvider() throws IOException {
        EnrichmentProvider ep = aic.getEnrichmentProvider("gcp.imageLabels");
        assertThat(ep).isNotNull();

        BlobTextFromDocument btd = setupBlobTextFromDocument("wakeupcat.jpg");
        Collection<EnrichmentMetadata> metadataCollection = ep.enrich(btd);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata metadata = metadataCollection.iterator().next();
        assertNotNull(metadata);
        assertEquals(btd.getRepositoryName(), metadata.context.repositoryName);
        assertEquals(btd.getId(), metadata.context.documentRef);
        assertEquals(1, metadata.context.digests.size());
        assertEquals(btd.getBlobs().entrySet().iterator().next().getValue().getDigest(),
                metadata.context.digests.iterator().next());
        assertThat(metadata.getLabels()).isNotEmpty();
        boolean thereIsCat = metadata.getLabels()
                                     .stream()
                                     .flatMap(lab -> lab.getValues().stream())
                                     .anyMatch(label -> label.getName().equalsIgnoreCase("cat"));
        assertThat(thereIsCat).isTrue();
    }

    @Test
    public void shouldCallGCPTextProvider() throws IOException {
        EnrichmentProvider ep = aic.getEnrichmentProvider("gcp.textDetection");
        assertThat(ep).isNotNull();

        BlobTextFromDocument btd = setupBlobTextFromDocument("wakeupcat.jpg");
        Collection<EnrichmentMetadata> metadataCollection = ep.enrich(btd);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata metadata = metadataCollection.iterator().next();
        assertNotNull(metadata);
        assertEquals(btd.getRepositoryName(), metadata.context.repositoryName);
        assertEquals(btd.getId(), metadata.context.documentRef);
        assertEquals(1, metadata.context.digests.size());
        assertEquals(btd.getBlobs().entrySet().iterator().next().getValue().getDigest(),
                metadata.context.digests.iterator().next());
        assertThat(metadata.getTags()).isNotEmpty();
        boolean thereIsCat = metadata.getTags()
                                     .stream()
                                     .flatMap(tag -> tag.getValues().stream())
                                     .anyMatch(tag -> tag.name.equals("Wake up human!\n"));
        assertThat(thereIsCat).isTrue();
    }

    @Test
    public void shouldCallGCPFaceProvider() throws IOException {
        EnrichmentProvider ep = aic.getEnrichmentProvider("gcp.faceDetection");
        assertThat(ep).isNotNull();

        BlobTextFromDocument btd = setupBlobTextFromDocument("balmer.jpg");
        Collection<EnrichmentMetadata> metadataCollection = ep.enrich(btd);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata metadata = metadataCollection.iterator().next();
        assertNotNull(metadata);
        assertEquals(btd.getRepositoryName(), metadata.context.repositoryName);
        assertEquals(btd.getId(), metadata.context.documentRef);
        assertEquals(1, metadata.context.digests.size());
        assertEquals(btd.getBlobs().entrySet().iterator().next().getValue().getDigest(),
                metadata.context.digests.iterator().next());
        assertThat(metadata.getTags()).isNotEmpty();
        boolean thereIsJoy = metadata.getTags()
                                     .get(0)
                                     .getValues()
                                     .get(0).features.stream()
                                                     .filter(tag -> tag.getName().equals("joy"))
                                                     .anyMatch(tag -> tag.getConfidence() > 2);
        assertThat(thereIsJoy).isTrue();
    }

    @Test
    public void shouldCallGCPLogoProvider() throws IOException {
        EnrichmentProvider ep = aic.getEnrichmentProvider("gcp.logoDetection");
        assertThat(ep).isNotNull();

        BlobTextFromDocument btd = setupBlobTextFromDocument("logo.jpg");
        Collection<EnrichmentMetadata> metadataCollection = ep.enrich(btd);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata metadata = metadataCollection.iterator().next();
        assertNotNull(metadata);
        assertEquals(btd.getRepositoryName(), metadata.context.repositoryName);
        assertEquals(btd.getId(), metadata.context.documentRef);
        assertEquals(1, metadata.context.digests.size());
        assertEquals(btd.getBlobs().entrySet().iterator().next().getValue().getDigest(),
                metadata.context.digests.iterator().next());
        assertThat(metadata.getTags()).isNotEmpty();
        List<String> tags = metadata.getTags()
                .get(0)
                .getValues()
                .stream().map(tag -> tag.name).collect(Collectors.toList());
        assertThat(tags).contains("Google");
    }

    @Test
    public void shouldCallGCPLandmarkProvider() throws IOException {
        EnrichmentProvider ep = aic.getEnrichmentProvider("gcp.landmarkDetection");
        assertThat(ep).isNotNull();

        BlobTextFromDocument btd = setupBlobTextFromDocument("sacre_coeur.jpg");
        Collection<EnrichmentMetadata> metadataCollection = ep.enrich(btd);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata metadata = metadataCollection.iterator().next();
        assertNotNull(metadata);
        assertEquals(btd.getRepositoryName(), metadata.context.repositoryName);
        assertEquals(btd.getId(), metadata.context.documentRef);
        assertEquals(1, metadata.context.digests.size());
        assertEquals(btd.getBlobs().entrySet().iterator().next().getValue().getDigest(),
                metadata.context.digests.iterator().next());
        assertThat(metadata.getTags()).isNotEmpty();
        List<String> tags = metadata.getTags()
                .get(0)
                .getValues()
                .stream().map(tag -> tag.name).collect(Collectors.toList());
        assertThat(tags).contains("Sacré-Cœur");
    }

    @Test
    public void shouldCallGCPObjectLocalizer() throws IOException {
        EnrichmentProvider ep = aic.getEnrichmentProvider("gcp.objectLocalizer");
        assertThat(ep).isNotNull();

        BlobTextFromDocument btd = setupBlobTextFromDocument("object_detection.jpeg");
        Collection<EnrichmentMetadata> metadataCollection = ep.enrich(btd);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata metadata = metadataCollection.iterator().next();
        assertNotNull(metadata);
        assertEquals(btd.getRepositoryName(), metadata.context.repositoryName);
        assertEquals(btd.getId(), metadata.context.documentRef);
        assertEquals(1, metadata.context.digests.size());
        assertEquals(btd.getBlobs().entrySet().iterator().next().getValue().getDigest(),
                metadata.context.digests.iterator().next());
        assertThat(metadata.getTags()).isNotEmpty();
        List<String> tags = metadata.getTags()
                .get(0)
                .getValues()
                .stream().map(tag -> tag.name).collect(Collectors.toList());
        assertThat(tags).contains("Bicycle");
    }

    @NotNull
    protected BlobTextFromDocument setupBlobTextFromDocument(String name) throws IOException {
        BlobProvider blobProvider = manager.getBlobProvider("test");
        File file = FileUtils.getResourceFileFromContext("files/" + name);
        assertThat(file.isFile()).isTrue();
        Blob blob = Blobs.createBlob(file, "image/jpg");
        blob.setFilename(file.getName());

        ManagedBlob managedBlob = blob(blob, blobProvider.writeBlob(blob));

        BlobTextFromDocument blobTextFromDoc = new BlobTextFromDocument();
        blobTextFromDoc.setRepositoryName(session.getRepositoryName());
        blobTextFromDoc.setId(UUID.randomUUID().toString());
        blobTextFromDoc.addBlob("file:content", IMAGE_TYPE, managedBlob);
        return blobTextFromDoc;
    }

    protected ManagedBlob blob(Blob blob, String key) {
        return new BlobMetaImpl("test", blob.getMimeType(), key, key, blob.getEncoding(), blob.getLength());
    }
}
