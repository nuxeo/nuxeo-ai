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
 *     jgarzon <jgarzon@nuxeo.com>
 */
package org.nuxeo.ai.imagequality;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentProvider;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, PlatformFeature.class})
@Deploy({"org.nuxeo.ai.nuxeo-ai-image-quality-core",
        "org.nuxeo.ai.nuxeo-ai-image-quality-core:OSGI-INF/test-image-quality.xml"})
public class TestImageQualityService {

    // This is used to mock the external service.  See mappings/check.json.
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(5078);

    @Inject
    protected AIComponent aiComponent;

    @Inject
    protected BlobManager manager;

    @Test(expected = IllegalArgumentException.class)
    public void basicServiceTest() {
        EnrichmentProvider service = aiComponent.getEnrichmentProvider("ai.imagequality.bad");
        assertNotNull(service);
    }

    @Test
    public void enrichmentTest() throws IOException {
        EnrichmentProvider service = aiComponent.getEnrichmentProvider("ai.imagequality.mock.fail");
        assertNotNull(service);

        Collection<AIMetadata> metadata =
                service.enrich(EnrichmentTestFeature.blobTestImage(manager));
        assertEquals("Service must fail gracefully.", 0, metadata.size());

        service = aiComponent.getEnrichmentProvider("ai.imagequality.mock");
        metadata = (List<EnrichmentMetadata>) service.enrich(EnrichmentTestFeature.blobTestImage(manager));
        assertEquals(1, metadata.size());
        assertEquals(17, metadata.get(0).getLabels().stream().mapToInt(l -> l.getValues().size()).sum());
        assertEquals(4, metadata.get(0).getTags().stream().mapToInt(l -> l.getValues().size()).sum());
    }

    @Test
    @Ignore("must be run manually because it calls an external service")
    /**
     *  Calls the real siteengine service instead of a mock. For this to work you will need to set your
     *  service nuxeo.ai.sightengine.apiKey & nuxeo.ai.sightengine.apiSecret (see the bottom of test-image-quality.xml).
     *  It is ignored so it doesn't run on Jenkins.
     */
    public void realServiceTest() throws IOException {
        EnrichmentProvider service = aiComponent.getEnrichmentProvider("ai.imagequality.real");
        assertNotNull(service);

        Collection<AIMetadata> metadata = service.enrich(EnrichmentTestFeature.blobTestImage(manager));
        assertEquals("Service must call successfully.", 1, metadata.size());
    }

}
