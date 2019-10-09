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

import static org.nuxeo.ai.transcribe.TranscribeWork.DEFAULT_CONVERSION;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.video.TranscodedVideo;
import org.nuxeo.ecm.platform.video.VideoDocument;
import org.nuxeo.runtime.api.Framework;

public class AudioConversionReady implements PostCommitEventListener {

    private static final Logger log = LogManager.getLogger(AudioConversionReady.class);

    @Override
    public void handleEvent(EventBundle eb) {
        eb.forEach(this::handleEvent);
    }

    protected void handleEvent(Event event) {
        DocumentEventContext ctx = (DocumentEventContext) event.getContext();
        if (ctx == null) {
            return;
        }

        DocumentModel doc = ctx.getSourceDocument();
        VideoDocument video = doc.getAdapter(VideoDocument.class);
        if (video == null) {
            log.warn("Video conversion done happened not on a video; doc id = " + doc.getId());
            return;
        }

        TranscodedVideo tv = video.getTranscodedVideo(DEFAULT_CONVERSION);
        if (tv == null) {
            log.warn("WAV is not ready; doc id = " + doc.getId());
            return;
        }

        TranscribeWork work = new TranscribeWork(doc.getRepositoryName(), doc.getId());
        Framework.getService(WorkManager.class).schedule(work);
    }
}
