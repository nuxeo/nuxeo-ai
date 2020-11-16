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
package org.nuxeo.ai.conversions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.platform.video.VideoConstants.INFO_PROPERTY;

import java.io.File;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import org.assertj.core.api.Condition;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.video.TranscodedVideo;
import org.nuxeo.ecm.platform.video.VideoDocument;
import org.nuxeo.ecm.platform.video.service.VideoService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.transaction.TransactionHelper;

@RunWith(FeaturesRunner.class)
@Features({CoreFeature.class})
@Deploy("org.nuxeo.ecm.platform.commandline.executor")
@Deploy("org.nuxeo.ecm.actions")
@Deploy("org.nuxeo.ecm.platform.picture.core")
@Deploy("org.nuxeo.ecm.automation.core")
@Deploy("org.nuxeo.ecm.platform.rendition.api")
@Deploy("org.nuxeo.ecm.platform.rendition.core")
@Deploy("org.nuxeo.ecm.platform.video")
@Deploy("org.nuxeo.ecm.platform.tag")
@Deploy("org.nuxeo.ai.ai-core")
public class TestVideoConversions {

    @Inject
    protected VideoService vs;

    @Inject
    protected ConversionService cs;

    @Inject
    protected EventService es;

    @Inject
    protected WorkManager wm;

    @Inject
    protected CoreSession session;

    @Test
    public void shouldContainCustomContribution() {
        assertThat(vs.getAvailableVideoConversions()).hasSize(4);
        assertThat(vs.getVideoConversion("WAV 16K")).isNotNull();
        assertTrue(cs.isConverterAvailable("convertToWAV16K").isAvailable());
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-core-test:OSGI-INF/disable-defult-video-conv-test.xml")
    @SuppressWarnings("unchecked")
    public void shouldRetrieveAudio() throws InterruptedException {
        File vf = FileUtils.getResourceFileFromContext("files/video240_short.mp4");
        DocumentModel dm = session.createDocumentModel("/", "testVideo", "Video");
        dm.setPropertyValue("dc:title", "testVideo");
        FileBlob blob = new FileBlob(vf);
        blob.setMimeType("video/mp4");
        blob.setFilename("video240.mp4");
        dm.setPropertyValue("file:content", blob);

        dm = session.createDocument(dm);
        assertNotNull(dm);
        session.save();

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        es.waitForAsyncCompletion();
        wm.awaitCompletion(5, TimeUnit.MINUTES);

        DocumentModel resDoc = session.getDocument(new IdRef(dm.getId()));

        Map<String, Serializable> info = (Map<String, Serializable>) resDoc.getPropertyValue(INFO_PROPERTY);
        assertThat(info.get("duration")).isEqualTo(30.03);

        VideoDocument adapter = resDoc.getAdapter(VideoDocument.class);
        adapter.getVideo();
        TranscodedVideo convert = vs.convert(adapter.getVideo(), "WAV 16K");
        assertNotNull(convert);
        assertThat(convert.getBlob()).isNotNull()
                .is(new Condition<Blob>() {
                    @Override
                    public boolean matches(Blob value) {
                        String ext = FileUtils.getFileExtension(value.getFilename()).toLowerCase();
                        return value.getLength() > 0 && "wav".equals(ext);
                    }
                });
    }
}
