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
package org.nuxeo.ai.caption;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({PlatformFeature.class})
@Deploy("org.nuxeo.ai.ai-core")
public class CaptionBaseTest {

    @Inject
    protected CoreSession session;

    @Test
    public void shouldBeAbleToAddCaptions2Doc() {
        DocumentModel doc = session.createDocumentModel("/", "MyDoc", "File");
        doc.addFacet("Captionable");
        doc = session.createDocument(doc);
        assertNotNull(doc);

        assertTrue(doc.hasFacet("Captionable"));
        Object captionable = doc.getProperties("caption").get("cap:captions");
        assertNotNull(captionable);
        assertTrue(captionable.getClass().isAssignableFrom(ArrayList.class));

        Map<String, Serializable> enUS = new HashMap<>();
        enUS.put("vtt", new StringBlob("Let's pretend it is an en_US VTT file"));
        enUS.put("lang", "en_US");

        List<Map<String, Serializable>> map = List.of(enUS);

        doc.setPropertyValue("cap:captions", (Serializable) map);
        doc = session.saveDocument(doc);
        assertNotNull(doc);
        @SuppressWarnings("unchecked")
        List<Map<String, Serializable>> captions = (List<Map<String, Serializable>>) doc.getPropertyValue("cap:captions");
        assertNotNull(captions);
        assertThat(captions).isNotEmpty()
                .hasSize(1);
    }
}
