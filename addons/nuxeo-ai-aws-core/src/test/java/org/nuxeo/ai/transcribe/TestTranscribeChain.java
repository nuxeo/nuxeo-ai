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
package org.nuxeo.ai.transcribe;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ai.transcribe.TranscribeWork.TRANSCRIBE_NORM_PROP;
import static org.nuxeo.ai.transcribe.TranscribeWork.TRANSCRIBE_RAW_PROP;
import static org.nuxeo.ai.transcribe.TranscribeWork.TRANSCRIBE_LABEL_PROP;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.AWS;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.fasterxml.jackson.databind.ObjectMapper;

@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
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
@Deploy("org.nuxeo.ecm.default.config")
@Deploy("org.nuxeo.ai.ai-core")
@Deploy("org.nuxeo.ai.aws.aws-core")
@Deploy("org.nuxeo.runtime.aws")
@Deploy("org.nuxeo.ai.ai-core-test:OSGI-INF/disable-defult-video-conv-test.xml")
public class TestTranscribeChain {

    @Inject
    protected CoreSession session;

    @Inject
    protected EventService es;

    @Inject
    protected WorkManager wm;

    @Inject
    protected TransactionalFeature tf;

    @Test
    public void shouldTranscribeVideo() throws IOException, InterruptedException {
        AWS.assumeCredentials();
        File mp4 = FileUtils.getResourceFileFromContext("files/video240_short.mp4");
        Blob blob = Blobs.createBlob(mp4, "video/mp4");

        DocumentModel doc = session.createDocumentModel("/", "testVideo", "Video");
        doc.setPropertyValue("dc:title", "TestVideo");
        doc.setPropertyValue("file:content", (Serializable) blob);

        doc = session.createDocument(doc);
        assertNotNull(doc);
        session.save();

        /* wait for {@link org.nuxeo.ecm.platform.video.service.VideoInfoWork}*/
        es.waitForAsyncCompletion();
        wm.awaitCompletion(5, TimeUnit.MINUTES);

        es.waitForAsyncCompletion();
        wm.awaitCompletion("videoConversion", 5, TimeUnit.MINUTES);

        es.waitForAsyncCompletion();
        wm.awaitCompletion("transcribeWork", 5, TimeUnit.MINUTES);
        tf.nextTransaction();

        doc = session.getDocument(doc.getRef());
        assertNotNull(doc);
        String results = (String) doc.getPropertyValue(TRANSCRIBE_LABEL_PROP);
        assertThat(results).isNotEmpty().hasLineCount(5);
        Blob raw = (Blob) doc.getPropertyValue(TRANSCRIBE_RAW_PROP);
        assertNotNull(raw);
        ObjectMapper mapper = new ObjectMapper();
        int rawSize = 0;
        try (InputStream is = raw.getStream()) {
            List list = mapper.readValue(is, List.class);
            assertNotNull(list);
            assertThat(list).isNotEmpty();
            rawSize = list.size();
            assertTrue(rawSize > 30);
        }

        Blob norm = (Blob) doc.getPropertyValue(TRANSCRIBE_NORM_PROP);
        assertNotNull(norm);
        try (InputStream is = norm.getStream()) {
            List list = mapper.readValue(is, List.class);
            assertNotNull(list);
            assertThat(list).isNotEmpty();
            assertTrue(rawSize > list.size());
        }
    }
}
