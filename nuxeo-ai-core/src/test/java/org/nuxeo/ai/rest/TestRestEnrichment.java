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
package org.nuxeo.ai.rest;

import static com.tngtech.jgiven.impl.util.AssertionUtil.assertNotNull;
import static junit.framework.TestCase.assertEquals;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentService;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.stream.pipes.services.JacksonUtil;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, PlatformFeature.class})
@Deploy({"org.nuxeo.ai.ai-core:OSGI-INF/enrichment-rest-test.xml"})
public class TestRestEnrichment {

    @Inject
    protected AIComponent aiComponent;

    @Test
    public void testCallService() throws IOException {
        assertNotNull(aiComponent);
        EnrichmentService service = aiComponent.getEnrichmentService("rest1");

        BlobTextStream blobTextStream = new BlobTextStream("docId", "default", "parent", "File", null);
        blobTextStream.setBlob(new BlobMetaImpl("test", "application/pdf", "xyx", "xyz", null, 45L));
        Collection<EnrichmentMetadata> results = service.enrich(blobTextStream);
        assertEquals(1, results.size());
        EnrichmentMetadata metadata = results.iterator().next();
        assertNotNull(metadata.getRawKey());

        TransientStore transientStore = aiComponent.getTransientStoreForEnrichmentService(metadata.getServiceName());
        List<Blob> rawBlobs = transientStore.getBlobs(metadata.getRawKey());
        assertEquals(1, rawBlobs.size());
        String raw = rawBlobs.get(0).getString();
        JsonNode jsonTree = JacksonUtil.MAPPER.readTree(raw);
        assertNotNull(jsonTree);

        service = aiComponent.getEnrichmentService("rest2");
        results = service.enrich(blobTextStream);
        assertEquals("Called unsuccessfully so the error must be handled and an empty result returned",
                     0, results.size());

    }
}
