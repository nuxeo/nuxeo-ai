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
import static org.junit.Assert.assertEquals;
import static org.nuxeo.runtime.stream.pipes.events.JacksonUtil.fromRecord;
import static org.nuxeo.runtime.stream.pipes.events.JacksonUtil.toRecord;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
public class TestEnrichmentMetaData {

    @Test
    public void testBuilder() {
        EnrichmentMetadata metadata = new EnrichmentMetadata.Builder("me", "m1", "doc1").build();
        assertNotNull(metadata);
    }

    @Test
    public void testJson() {
        EnrichmentMetadata metadata =
                new EnrichmentMetadata.Builder("me", "m1", "doc1")
                        .withBlobDigest("blobxx")
                        .withTargetDocumentProperty("tbloby").build();
        assertNotNull(metadata);
        Record record = toRecord("k", metadata);
        EnrichmentMetadata metadataBackAgain = fromRecord(record, EnrichmentMetadata.class);
        assertEquals(metadata, metadataBackAgain);

    }
}
