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
package org.nuxeo.runtime.stream.pipes;


import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.Serializable;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.stream.pipes.events.RecordUtil;
import org.nuxeo.runtime.stream.pipes.pipes.DocumentPipeFunction;
import org.nuxeo.runtime.stream.pipes.types.DocStream;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({PlatformFeature.class})
public class RecordsTest {

    public static final String TEST_MIME_TYPE = "text/plain";

    @Inject
    CoreSession session;

    @Test
    public void TestEventsToRecord() throws Exception {
        DocumentPipeFunction func = new DocumentPipeFunction();
        Record r = func.transformation.apply(getTestEvent());
        assertNotNull(r);

        DocStream andBack = RecordUtil.fromRecord(r, DocStream.class);
        assertNotNull(andBack);
        assertEquals("File", andBack.primaryType);
        assertEquals(7, andBack.getBlobs().get("default").getLength());
        assertEquals(TEST_MIME_TYPE, andBack.getBlobs().get("default").getMimeType());
    }

    @Test(expected = NuxeoException.class)
    public void TestToRecord() throws Exception {
        RecordUtil.toRecord("akey", getTestEvent());
    }

    @Test(expected = NuxeoException.class)
    public void TestFromRecord() throws Exception {
        String value = "Some test";
        RecordUtil.fromRecord(Record.of("33", value.getBytes("UTF-8")), DocumentModel.class);
    }

    @NotNull
    protected Event getTestEvent() throws Exception {

        DocumentModel doc = session.createDocumentModel("/", "My Doc", "File");
        Blob blob = Blobs.createBlob("My text", TEST_MIME_TYPE);
        doc.setPropertyValue("file:content", (Serializable) blob);
        doc = session.createDocument(doc);
        session.save();
        EventContextImpl evctx = new DocumentEventContext(session, session.getPrincipal(), doc);
        Event event = evctx.newEvent("myDocEvent");
        event.setInline(true);
        assertNotNull(event);
        return event;
    }
}