/*
 * (C) Copyright 2026 Hyland (http://hyland.com/) and others.
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
 */
package org.nuxeo.ai.contentintelligence;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import jakarta.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
@Deploy("org.nuxeo.ai.nuxeo-ai-content-intelligence-core:OSGI-INF/content-intelligence-service.xml")
public class TestContentIntelligenceComponent {

    @Inject
    protected ContentIntelligenceService service;

    @Test
    public void shouldExposeServiceWithDefaultConfiguration() {
        assertNotNull(service);
        // without nuxeo.hyland.cic.* properties the default configuration is incomplete: calls fail clearly
        ContentIntelligenceRequest request = ContentIntelligenceRequest.of("image-description", null, null);
        NuxeoException e = assertThrows(NuxeoException.class,
                () -> service.enrich("default", Blobs.createBlob("hello", "image/jpeg"), request));
        assertTrue(e.getMessage(), e.getMessage().contains("'default'"));
        assertTrue(e.getMessage(), e.getMessage().contains("nuxeo.hyland.cic"));
    }
}
