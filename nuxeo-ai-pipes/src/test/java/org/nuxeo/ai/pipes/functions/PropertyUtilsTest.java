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
package org.nuxeo.ai.pipes.functions;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.nuxeo.ai.pipes.events.EventPipesTest.TEST_MIME_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.base64EncodeBlob;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.getPropertyValue;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.notNull;

import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({PlatformFeature.class})
public class PropertyUtilsTest {

    public static final String FILE_CONTENT = "file:content";

    @Inject
    protected CoreSession session;

    protected static String stringProp(DocumentModel doc, String prop) {
        return getPropertyValue(doc, prop, String.class);
    }

    @Test
    public void testGetTextProps() {
        DocumentModel doc = session.createDocumentModel("/", "My Doc with props", "File");
        doc.addFacet("Publishable");
        doc.addFacet("Versionable");
        doc = session.createDocument(doc);

        assertEquals(doc.getPathAsString(), stringProp(doc, "ecm:path"));
        assertEquals(doc.getId(), stringProp(doc, "ecm:uuid"));
        assertEquals(doc.getName(), stringProp(doc, "ecm:name"));
        assertEquals(doc.getTitle(), stringProp(doc, "ecm:title"));
        assertEquals(doc.getRepositoryName(), stringProp(doc, "ecm:repository"));
        assertEquals(doc.getParentRef().toString(), stringProp(doc, "ecm:parentId"));
        assertEquals(doc.getVersionSeriesId(), stringProp(doc, "ecm:versionVersionableId"));
        assertEquals("false", stringProp(doc, "ecm:isVersion"));
        assertEquals("false", stringProp(doc, "ecm:isProxy"));
        assertEquals("false", stringProp(doc, "ecm:isTrashed"));
        assertEquals("false", stringProp(doc, "ecm:isCheckedIn"));
        assertEquals("false", stringProp(doc, "ecm:isLatestVersion"));
        assertEquals("false", stringProp(doc, "ecm:isLatestMajorVersion"));
        assertEquals("0.0", stringProp(doc, "ecm:versionLabel"));
        assertEquals(Boolean.FALSE, getPropertyValue(doc, "ecm:isProxy", Boolean.class));
        assertEquals("File", stringProp(doc, "ecm:primaryType"));
        assertEquals("Versionable,Publishable,Commentable,HasRelatedText,Downloadable",
                     stringProp(doc, "ecm:mixinType"));
        assertEquals("project", stringProp(doc, "ecm:currentLifeCycleState"));
        assertEquals("Administrator", stringProp(doc, "dc:creator"));
        Instant created = getPropertyValue(doc, "dc:created", Calendar.class).toInstant();
        // This line tests that a date is returned as an ISO-8601 String.
        assertEquals(created, Instant.parse(stringProp(doc, "dc:created")));

        assertNull(stringProp(doc, "ecm:pos"));
        assertNull(stringProp(doc, "ecm:nope"));

    }

    @Test
    public void testGetBlobProps() throws IOException {
        DocumentModel doc = session.createDocumentModel("/", "My Doc with blobs", "File");
        Blob blob = Blobs.createBlob("My text is not very long.", TEST_MIME_TYPE);
        doc.setPropertyValue(FILE_CONTENT, (Serializable) blob);
        doc = session.createDocument(doc);
        assertEquals(doc.getId(), stringProp(doc, "ecm:uuid"));
        Blob textBlob = getPropertyValue(doc, FILE_CONTENT, Blob.class);
        assertEquals(blob.getLength(), textBlob.getLength());
        assertEquals(base64EncodeBlob(blob), getPropertyValue(doc, FILE_CONTENT, String.class));
        textBlob.getFile().delete();
        assertNull(base64EncodeBlob(textBlob));
    }

    @Test
    public void testCheckNullProps() {
        DocumentModel doc = session.createDocumentModel("/", "My Not null Doc", "File");
        doc = session.createDocument(doc);
        assertTrue(notNull(doc, "ecm:name"));
        assertFalse(notNull(doc, "ecm:nope"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testMultivalue() {
        DocumentModel doc = session.createDocumentModel("/", "Multi", "File");
        List<String> subjects = Arrays.asList("birds", "flowers");
        doc.setPropertyValue("dc:subjects", (Serializable) subjects);
        doc = session.createDocument(doc);
        stringProp(doc, "dc:subjects");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testComplexConvert() {
        DocumentModel doc = session.createDocumentModel("/", "Complex", "File");
        doc = session.createDocument(doc);
        getPropertyValue(doc, "ecm:isTrashed", Integer.class);
    }

}
