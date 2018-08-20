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
 *     Pedro Cardoso
 */
package org.nuxeo.ai.model.publishing;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.IOException;

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
@Features(PlatformFeature.class)
@Deploy({ "org.nuxeo.ai.ai-model", "org.nuxeo.ai.ai-model:OSGI-INF/model-publishing-test.xml" })
public class TestCustomModelFileSystemPublish {

    @Inject
    protected ModelPublishingService modelPublishingService;

    @Inject
    protected CoreSession session;

    @Test
    public void testService() {
        assertNotNull(modelPublishingService);
    }

    @Test
    public void testModelPublishFileSystem() throws IOException {

        String modelName = "model_test";
        int modelVersion = 1;
        DocumentModel doc = session.createDocumentModel("/", "testModel", "AI_Model");
        doc = session.createDocument(doc);

        assertFalse(modelPublishingService.isModelPublished(doc.getId()));
        modelPublishingService.publishModel(doc.getId());
        assertTrue(modelPublishingService.isModelPublished(doc.getId()));
        try {
            modelPublishingService.publishModel(doc.getId());
            fail();
        } catch (IOException ex) {
            assertTrue(ex.getMessage(), ex.getMessage().contains("model folder already exists"));
        }
        modelPublishingService.unpublishModel(doc.getId());
        assertFalse(modelPublishingService.isModelPublished(doc.getId()));
    }
}
