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
import static org.nuxeo.runtime.stream.pipes.events.EventPipesTest.TEST_MIME_TYPE;
import static org.nuxeo.runtime.stream.pipes.events.EventPipesTest.getTestEvent;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.stream.pipes.events.RecordUtil;
import org.nuxeo.runtime.stream.pipes.pipes.DocumentPipeFunction;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({PlatformFeature.class})
public class RecordsTest {

    @Inject
    CoreSession session;

    @Test
    public void TestEventsToRecord() throws Exception {
        DocumentPipeFunction func = new DocumentPipeFunction();

        List<Record> recs = (List<Record>) func.transformation.apply(getTestEvent(session));
        assertNotNull(recs);

        BlobTextStream andBack = RecordUtil.fromRecord(recs.get(0), BlobTextStream.class);
        assertNotNull(andBack);
        assertEquals("File", andBack.getPrimaryType());
        assertEquals("file:content", andBack.getBlobId());
        assertEquals(7, andBack.getBlob().getLength());
        assertEquals(TEST_MIME_TYPE, andBack.getBlob().getMimeType());
    }

    @Test(expected = NuxeoException.class)
    public void TestToRecord() throws Exception {
        RecordUtil.toRecord("akey", getTestEvent(session));
    }

    @Test(expected = NuxeoException.class)
    public void TestFromRecord() throws Exception {
        String value = "Some test";
        RecordUtil.fromRecord(Record.of("33", value.getBytes("UTF-8")), DocumentModel.class);
    }

}