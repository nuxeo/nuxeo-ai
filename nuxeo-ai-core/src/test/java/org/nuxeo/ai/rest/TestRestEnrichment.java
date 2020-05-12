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


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.enrichment.EnrichmentTestFeature.FILE_CONTENT;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentProvider;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.pipes.services.JacksonUtil;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import com.fasterxml.jackson.databind.JsonNode;

@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, PlatformFeature.class})
@Deploy({"org.nuxeo.ai.ai-core:OSGI-INF/enrichment-rest-test.xml"})
public class TestRestEnrichment {

    @Inject
    protected AIComponent aiComponent;

    @Test
    public void testCallService() throws IOException {
        assertNotNull(aiComponent);
        EnrichmentProvider service = aiComponent.getEnrichmentProvider("rest1");

        BlobTextFromDocument blobTextFromDoc = new BlobTextFromDocument("docId", "default", "parent", "File", null);
        blobTextFromDoc.addBlob(FILE_CONTENT, "img", new BlobMetaImpl("test", "application/pdf", "xyx", "xyz", null, 45L));
        Collection<EnrichmentMetadata> results = service.enrich(blobTextFromDoc);
        assertEquals(1, results.size());
        EnrichmentMetadata metadata = results.iterator().next();
        assertNotNull(metadata.getRawKey());

        TransientStore transientStore = aiComponent.getTransientStoreForEnrichmentProvider(metadata.getModelName());
        List<Blob> rawBlobs = transientStore.getBlobs(metadata.getRawKey());
        assertEquals(1, rawBlobs.size());
        String raw = rawBlobs.get(0).getString();
        JsonNode jsonTree = JacksonUtil.MAPPER.readTree(raw);
        assertNotNull(jsonTree);

        service = aiComponent.getEnrichmentProvider("rest2");
        results = service.enrich(blobTextFromDoc);
        assertEquals("Called unsuccessfully so the error must be handled and an empty result returned",
                     0, results.size());

    }

    @Test
    public void testIsLive() {
        Map<String, String> options = new HashMap<>();
        String prefix = "testing.";
        options.put(prefix + "uri", "http://explorer.nuxeo.com/nuxeo/runningstatus");
        options.put(prefix + "methodName", "GET");
        assertTrue(RestClient.isLive(options, prefix));
    }

    @Test
    public void testRestHeaders() {
        Map<String, String> options = new HashMap<>();
        String prefix = "testing.";
        options.put(prefix + "uri", "http://explorer.nuxeo.com/nuxeo/runningstatus");
        options.put(prefix + "header.xyz", "myhead");
        options.put(prefix + "header.123", "small head");
        options.put(prefix + "header.546", "big head");
        RestClient client = new RestClient(options, prefix, null);
        assertEquals("myhead", client.headers.stream()
                                             .filter(h -> "xyz".equals(h.getName())).findFirst().get().getValue());
        assertEquals("small head", client.headers.stream()
                                                 .filter(h -> "123".equals(h.getName())).findFirst().get().getValue());

        options.clear();
        options.put(prefix + "uri", "http://explorer.nuxeo.com/nuxeo/runningstatus");
        client = new RestClient(options, prefix, null);
        assertEquals("No config so we just have the default headers",
                     client.getDefaultHeaders().size(), client.headers.size());

        options.clear();
        options.put("uri", "http://explorer.nuxeo.com/nuxeo/runningstatus");
        options.put("header.X-Authentication-Token", "3456");
        client = new RestClient(options, null);
        assertEquals("3456", client.headers.stream()
                                           .filter(h -> "X-Authentication-Token".equals(h.getName())).findFirst().get()
                                           .getValue());
    }
}
