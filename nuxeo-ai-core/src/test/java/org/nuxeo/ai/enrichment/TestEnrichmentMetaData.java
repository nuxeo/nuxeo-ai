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

import static com.tngtech.jgiven.impl.util.AssertionUtil.assertNotNull;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.fromRecord;
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.toRecord;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
public class TestEnrichmentMetaData {

    final String repositoryName = "default";

    @Test
    public void testBuilder() {
        EnrichmentMetadata metadata = new EnrichmentMetadata.Builder(Instant.now(), "m1", "test",
                                                                     new AIMetadata.Context(repositoryName,
                                                                                            "doc1",
                                                                                            null,
                                                                                            null,
                                                                                            null)).build();
        assertNotNull(metadata);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalid() {
        new EnrichmentMetadata.Builder(Instant.now(), "m1", "test", null).build();
    }

    @Test
    public void testJson() throws IOException {
        List<EnrichmentMetadata.Label> labels = Stream.of("label1", "l2", "lab3")
                                                      .map(l -> new EnrichmentMetadata.Label(l, 1))
                                                      .collect(Collectors.toList());

        List<EnrichmentMetadata.Tag> tags =
                Stream.of("tag1", "tag2")
                      .map(l -> new EnrichmentMetadata.Tag(l,
                                                           "t1",
                                                           "myref" + l,
                                                           new AIMetadata.Box(0.5f, 0.3f, -0.2f, 2f),
                                                           singletonList(new EnrichmentMetadata.Label("f" + l, 1)),
                                                           0.65f))
                      .collect(Collectors.toList());
        BlobTextStream blobTextStream = new BlobTextStream("doc1", repositoryName, null, "File", null);
        blobTextStream.addXPath("tbloby");
        EnrichmentMetadata metadata =
                new EnrichmentMetadata.Builder("m1", "test", blobTextStream)
                        .withBlobDigest("blobxx")
                        .withLabels(labels)
                        .withTags(tags)
                        .withCreator("bob")
                        .withRawKey("xyz")
                        .build();
        assertNotNull(metadata);
        Record record = toRecord("k", metadata);
        EnrichmentMetadata metadataBackAgain = fromRecord(record, EnrichmentMetadata.class);
        assertEquals(metadata, metadataBackAgain);
        assertNotNull(metadataBackAgain.toString());

    }
}
