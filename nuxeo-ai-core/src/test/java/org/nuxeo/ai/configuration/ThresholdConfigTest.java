/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.configuration;

import static org.junit.Assert.assertEquals;

import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ ThresholdTestFeature.class, PlatformFeature.class })
public class ThresholdConfigTest {

    @Inject
    protected CoreSession coreSession;

    @Inject
    protected ThresholdService thresholdService;

    @Test
    @Deploy({ "org.nuxeo.ai.ai-core:OSGI-INF/threshold-config-test.xml" })
    public void shouldContainThresholdConfigs() {
        DocumentModel file = coreSession.createDocumentModel("/", "MyFile", "File");
        file.setPropertyValue("dc:title", "My File");
        file = coreSession.createDocument(file);

        float tshldTitle = thresholdService.getAutoCorrectThreshold(file, "dc:title");
        assertEquals(0.77, tshldTitle, 0.00001);

        float tshldContent = thresholdService.getAutoFillThreshold(file, "file:content");
        assertEquals("Default threshold on type is incorrect", 0.88, tshldContent, 0.0001);

        float tshldDescriptionBasedOnFacet = thresholdService.getAutoFillThreshold(file, "dc:description");
        assertEquals(0.65, tshldDescriptionBasedOnFacet, 0.00001);
    }

    @Test
    @Deploy({ "org.nuxeo.ai.ai-core:OSGI-INF/threshold-config-test.xml" })
    public void shouldNotContainValidThresholdConfig() {
        DocumentModel fakeDoc = coreSession.createDocumentModel("/", "FakeDoc", "Document");
        float thsldTitle = thresholdService.getAutoCorrectThreshold(fakeDoc, "dc:title");
        assertEquals(2.0f, thsldTitle, 0.01f);

        fakeDoc.addFacet("Commentable");
        thsldTitle = thresholdService.getAutoCorrectThreshold(fakeDoc, "dc:title");
        assertEquals("Global threshold wasn't applied", 0.5f, thsldTitle, 0.01f);
    }

    @Test
    public void shouldUseUsersConfig() {
        DocumentModel fakeDoc = coreSession.createDocumentModel("/", "FakeDoc", "Document");
        float thsldTitle = thresholdService.getThreshold(fakeDoc, "dc:title");
        assertEquals(0.99f, thsldTitle, 0.01f);
    }
}
