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
 *     mvachette
 */
package org.nuxeo.ai.transcribe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.listeners.VideoAboutToChange.CAPTIONABLE_FACET;
import static org.nuxeo.ai.services.DocMetadataService.ENRICHMENT_MODIFIED;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy("org.nuxeo.ai.ai-core")
@Deploy("org.nuxeo.ai.aws.aws-core")
public class TestTranscribeToCaptions {

    @Inject
    protected CoreSession session;

    @Inject
    protected EventService es;

    @Inject
    protected TransactionalFeature tf;

    DocumentModel fileDoc;

    @Before
    public void setup() throws IOException {
        fileDoc = session.createDocumentModel("/", "MyDoc", "File");
        fileDoc.addFacet(CAPTIONABLE_FACET);
        fileDoc.addFacet(ENRICHMENT_FACET);

        File jsonFile = FileUtils.getResourceFileFromContext("files/transcribe_resp.json");
        String json = new String(Files.readAllBytes(jsonFile.toPath()));

        Map<String, Serializable> item = new HashMap<>();
        item.put("model", "aws.transcribe");
        item.put("raw", (Serializable) Blobs.createJSONBlob(json));

        List<Map<String, Serializable>> items = Collections.singletonList(item);
        fileDoc.setPropertyValue("enrichment:items", (Serializable) items);
        fileDoc = session.createDocument(fileDoc);

        assertNotNull(fileDoc);
        session.save();
    }

    @Test
    public void shouldConvertToCaptions() {
        DocumentEventContext ctx = new DocumentEventContext(session, session.getPrincipal(), fileDoc);
        es.fireEvent(ctx.newEvent(ENRICHMENT_MODIFIED));
        es.waitForAsyncCompletion();
        tf.nextTransaction();

        DocumentModel doc = session.getDocument(fileDoc.getRef());

        @SuppressWarnings("unchecked")
        List<Map<String, Serializable>> prop = (List<Map<String, Serializable>>) doc.getPropertyValue("cap:captions");
        assertThat(prop).isNotNull().hasSize(1);
    }

    @Test
    public void shouldUpdateCaptions() {
        for (int i = 0; i < 2; i++) {
            DocumentEventContext ctx = new DocumentEventContext(session, session.getPrincipal(), fileDoc);
            es.fireEvent(ctx.newEvent(ENRICHMENT_MODIFIED));
            es.waitForAsyncCompletion();
            tf.nextTransaction();
        }

        DocumentModel doc = session.getDocument(fileDoc.getRef());

        @SuppressWarnings("unchecked")
        List<Map<String, Serializable>> prop = (List<Map<String, Serializable>>) doc.getPropertyValue("cap:captions");
        assertThat(prop).isNotNull().hasSize(1);
    }

}
