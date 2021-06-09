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

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ai.metadata.TagSuggestion;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.sdk.objects.PropertyType;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import javax.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.tngtech.jgiven.impl.util.AssertionUtil.assertNotNull;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.enrichment.EnrichmentTestFeature.FILE_CONTENT;
import static org.nuxeo.ai.enrichment.EnrichmentTestFeature.blobTestImage;
import static org.nuxeo.ai.pipes.services.JacksonUtil.fromRecord;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toRecord;

@RunWith(FeaturesRunner.class)
@Features({ EnrichmentTestFeature.class, PlatformFeature.class })
public class TestEnrichmentMetaData {

    final String repositoryName = "default";

    @Inject
    protected BlobManager blobManager;

    @Test
    public void testBuilder() {
        EnrichmentMetadata metadata = new EnrichmentMetadata.Builder(Instant.now(), "m1", "test",
                new AIMetadata.Context(repositoryName, "doc1", null, null)).build();
        assertNotNull(metadata);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalid() {
        new EnrichmentMetadata.Builder(Instant.now(), "m1", "test", (AIMetadata.Context) null).build();
        new EnrichmentMetadata.Builder(Instant.now(), "m1", "test", (AIMetadata.Context) null).build();
    }

    @Test
    public void testJson() {
        List<EnrichmentMetadata.Label> labels = Stream.of("label1", "l2", "lab3")
                                                      .map(l -> new EnrichmentMetadata.Label(l, 1, 0L))
                                                      .collect(Collectors.toList());

        List<EnrichmentMetadata.Tag> tags = Stream.of("tag1", "tag2")
                                                  .map(l -> new EnrichmentMetadata.Tag(l, "t1", "myref" + l,
                                                          new AIMetadata.Box(0.5f, 0.3f, -0.2f, 2f),
                                                          singletonList(new EnrichmentMetadata.Label("f" + l, 1, 0L)),
                                                          0.65f))
                                                  .collect(Collectors.toList());
        BlobTextFromDocument blobTextFromDoc = new BlobTextFromDocument("doc1", repositoryName, null, "File", null);
        blobTextFromDoc.addProperty("dc:title", "tbloby");
        EnrichmentMetadata metadata = new EnrichmentMetadata.Builder("m1", "test", blobTextFromDoc).withLabels(
                Collections.singletonList(new LabelSuggestion("my:property", labels)))
                                                                                                   .withTags(
                                                                                                           Collections.singletonList(
                                                                                                                   new TagSuggestion(
                                                                                                                           "my:property2",
                                                                                                                           tags)))
                                                                                                   .withDigest("blobxx")
                                                                                                   .withDigest(
                                                                                                           "freblogs")
                                                                                                   .withCreator("bob")
                                                                                                   .withRawKey("xyz")
                                                                                                   .build();
        assertNotNull(metadata);
        Record record = toRecord("k", metadata);
        EnrichmentMetadata metadataBackAgain = fromRecord(record, EnrichmentMetadata.class);
        assertEquals(metadata, metadataBackAgain);
        assertNotNull(metadataBackAgain.toString());

        EnrichmentMetadata suggest = EnrichmentUtils.copyMetadata(metadataBackAgain, blobTextFromDoc);
        assertNotNull(suggest);
        assertEquals(metadataBackAgain.getLabels(), metadata.getLabels());
        assertEquals(metadataBackAgain.getTags(), metadata.getTags());
    }

    @Test
    public void testRawJson() throws IOException {
        BlobTextFromDocument blobTextFromDoc = new BlobTextFromDocument("doc1", repositoryName, null, "File", null);
        EnrichmentMetadata metadata = new EnrichmentMetadata.Builder("m1", "test", blobTextFromDoc).withCreator("bob")
                                                                                                   .withRawKey("xyz")
                                                                                                   .build();
        assertTrue(StringUtils.isEmpty(EnrichmentUtils.getRawBlob(metadata)));
    }

    @Test
    public void testCacheKeys() throws IOException {
        BlobTextFromDocument blobTextFromDoc = blobTestImage(blobManager);
        PropertyType fileContentProp = PropertyType.of(FILE_CONTENT, "img");
        blobTextFromDoc.computePropertyBlobs().get(fileContentProp).setDigest("47XX");
        assertEquals("testin47XX", EnrichmentUtils.makeKeyUsingBlobDigests(blobTextFromDoc, "testin"));
        ManagedBlob blob = blobTextFromDoc.computePropertyBlobs().get(fileContentProp);
        blobTextFromDoc.addBlob("TEST_AGAIN", "img",
                new BlobMetaImpl(blob.getProviderId(), blob.getMimeType(), blob.getKey(), "58YY", blob.getEncoding(),
                        blob.getLength()));
        assertEquals("testin47XX_58YY", EnrichmentUtils.makeKeyUsingBlobDigests(blobTextFromDoc, "testin"));
    }
}
