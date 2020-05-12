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
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.nuxeo.ai.pipes.events.EventPipesTest.TEST_MIME_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.FILE_CONTENT;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.IMAGE_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.TEXT_TYPE;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.base64EncodeBlob;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.getPropertyValue;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.notNull;
import static org.nuxeo.ecm.core.storage.FulltextExtractorWork.SYSPROP_FULLTEXT_BINARY;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.pipes.types.PropertyNameType;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy("org.nuxeo.ecm.platform.commandline.executor")
@Deploy("org.nuxeo.ecm.actions")
@Deploy("org.nuxeo.ecm.platform.picture.api")
@Deploy("org.nuxeo.ecm.platform.picture.core")
@Deploy("org.nuxeo.ecm.platform.picture.convert")
@Deploy("org.nuxeo.ecm.automation.core")
@Deploy("org.nuxeo.ecm.platform.rendition.api")
@Deploy("org.nuxeo.ecm.platform.rendition.core")
@Deploy("org.nuxeo.ecm.platform.video.convert")
@Deploy("org.nuxeo.ecm.platform.video.core")
@Deploy("org.nuxeo.ecm.platform.tag")
public class PropertyUtilsTest {

    @Inject
    protected CoreSession session;

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected TransactionalFeature txFeature;

    protected static String stringProp(DocumentModel doc, String prop) {
        return getPropertyValue(doc, prop, String.class);
    }

    @Test
    public void testGetTextProps() {
        DocumentModel doc = session.createDocumentModel("/", "My Doc with props", "File");
        doc.addFacet("Publishable");
        doc.addFacet("Versionable");
        doc = session.createDocument(doc);
        session.setDocumentSystemProp(doc.getRef(), SYSPROP_FULLTEXT_BINARY, "My full text");
        session.save();
        coreFeature.getStorageConfiguration().sleepForFulltext();

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
        assertEquals("project", stringProp(doc, "ecm:currentLifeCycleState"));
        assertEquals("Administrator", stringProp(doc, "dc:creator"));
        Instant created = getPropertyValue(doc, "dc:created", Calendar.class).toInstant();
        // This line tests that a date is returned as an ISO-8601 String.
        assertEquals(created, Instant.parse(stringProp(doc, "dc:created")));
        assertEquals("My full text", stringProp(doc, NXQL.ECM_FULLTEXT));
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

    @Test
    public void testMultivalue() {
        DocumentModel doc = session.createDocumentModel("/", "Multi", "File");
        List<String> subjects = Arrays.asList("birds", "flowers");
        doc.setPropertyValue("dc:subjects", (Serializable) subjects);
        doc = session.createDocument(doc);
        assertEquals("birds | flowers", stringProp(doc, "dc:subjects"));
        assertEquals("Versionable | NXTag | Publishable | Commentable | HasRelatedText | Downloadable",
                stringProp(doc, "ecm:mixinType"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testComplexConvert() {
        DocumentModel doc = session.createDocumentModel("/", "Complex", "File");
        doc = session.createDocument(doc);
        getPropertyValue(doc, "ecm:isTrashed", Integer.class);
    }

    @Test
    public void iCanSerializeDoc() {
        // Test with blob text
        Set<PropertyNameType> properties = new HashSet<>(Arrays.asList(new PropertyNameType("dc:title",TEXT_TYPE),
                new PropertyNameType(FILE_CONTENT, TEXT_TYPE)));
        DocumentModel doc = session.createDocumentModel("/", "Text", "File");
        Blob textBlob = Blobs.createBlob("My text is not very long.", TEST_MIME_TYPE);
        doc.setPropertyValue(FILE_CONTENT, (Serializable) textBlob);
        doc = session.createDocument(doc);
        BlobTextFromDocument blobTextFromDocument = PropertyUtils.docSerialize(doc, properties);
        assertThat(blobTextFromDocument).isNotNull();
        ManagedBlob blobResult = blobTextFromDocument.getPropertyBlobs().get(new PropertyNameType(FILE_CONTENT, TEXT_TYPE));
        textBlob = (Blob) doc.getPropertyValue(FILE_CONTENT);
        assertThat(blobResult).isNotNull();
        assertThat(blobResult.getDigest()).isEqualTo(textBlob.getDigest());

        // Test with pictures
        properties = new HashSet<>(Arrays.asList(new PropertyNameType("dc:title",TEXT_TYPE),
                new PropertyNameType(FILE_CONTENT, IMAGE_TYPE)));
        doc = session.createDocumentModel("/", "picture", "Picture");
        File file = FileUtils.getResourceFileFromContext("files/plane.jpg");
        FileBlob imageBlob = new FileBlob(file);
        doc.setPropertyValue(FILE_CONTENT, imageBlob);
        doc = session.createDocument(doc);
        session.save();

        txFeature.nextTransaction();

        blobTextFromDocument = PropertyUtils.docSerialize(doc, properties);
        Blob image = (Blob) doc.getPropertyValue(FILE_CONTENT);
        assertThat(blobTextFromDocument).isNotNull();
        blobResult = blobTextFromDocument.getPropertyBlobs().get(new PropertyNameType(FILE_CONTENT, IMAGE_TYPE));
        assertThat(blobResult).isNotNull();
        // Confirm that we took a related picture view and not the original blob
        assertThat(blobResult.getDigest()).isNotEqualTo(image.getDigest());
        assertThat(blobResult.getFilename()).isEqualTo("Small_plane.jpg");
    }

}
