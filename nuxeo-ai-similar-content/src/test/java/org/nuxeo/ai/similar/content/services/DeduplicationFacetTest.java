/*
 * (C) Copyright 2006-2021 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 * Contributors:
 *    Andrei Nechaev
 *
 */

package org.nuxeo.ai.similar.content.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.apache.tomcat.jni.Time;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ CoreFeature.class })
@Deploy("org.nuxeo.ai.similar-content")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/dedup-config-test.xml")
@Deploy("org.nuxeo.ai.similar-content-test:OSGI-INF/disable-dedup-listener.xml")
public class DeduplicationFacetTest {

    @Inject
    protected CoreSession session;

    @Test
    public void shouldAddDeduplicableFacet() {
        DocumentModel doc = session.createDocumentModel("/", "TestDoc", "Document");
        doc = session.createDocument(doc);
        assertThat(doc).isNotNull();
        session.save();

        assertThat(doc.getFacets()).doesNotContain("Deduplicable");

        assertThat(doc.addFacet("Deduplicable")).isTrue();
        doc = session.saveDocument(doc);
        session.save();

        doc = session.getDocument(doc.getRef());
        assertThat(doc.getFacets()).contains("Deduplicable");
        assertThat(doc.getSchemas()).contains("deduplication");

        List<Map<String, Object>> history = new ArrayList<>(1);
        Map<String, Object> entry = new HashMap<>();
        entry.put("xpath", "file:content");
        entry.put("index", true);
        entry.put("date", new GregorianCalendar());
        history.add(entry);
        doc.setPropertyValue("dedup:history", (Serializable) history);

        doc = session.saveDocument(doc);
        session.save();

        doc = session.getDocument(doc.getRef());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> historyResult = (List<Map<String, Object>>) doc.getPropertyValue("dedup:history");
        assertThat(historyResult).isNotEmpty().contains(entry);
    }
}
