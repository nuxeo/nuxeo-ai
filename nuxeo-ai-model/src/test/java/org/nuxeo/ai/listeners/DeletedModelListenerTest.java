/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo
 */
package org.nuxeo.ai.listeners;

import static org.assertj.core.api.Java6Assertions.assertThat;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.model.serving.ModelServingService;
import org.nuxeo.ai.model.serving.RuntimeModel;
import org.nuxeo.ai.services.AIConfigurationService;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ CoreFeature.class })
@Deploy({ "org.nuxeo.ai.ai-core", "org.nuxeo.ai.ai-model" })
public class DeletedModelListenerTest {

    @Inject
    protected CoreSession session;

    @Inject
    protected ModelServingService modelService;

    @Inject
    protected AIConfigurationService aiConfigurationService;

    @Test
    public void shouldAddCaptionableFacet() {
        DocumentModel doc = session.createDocumentModel("/", "AI_Model", "Model");
        doc = session.createDocument(doc);
        aiConfigurationService.set(doc.getId(), "<xml>");
        RuntimeModel model = modelService.getModel(doc.getId());
        assertThat(model).isNotNull();
        session.removeDocument(doc.getRef());
        model = modelService.getModel(doc.getId());
        assertThat(model).isNull();
    }
}
