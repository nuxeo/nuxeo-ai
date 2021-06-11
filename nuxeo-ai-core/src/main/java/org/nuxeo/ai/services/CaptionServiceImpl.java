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
package org.nuxeo.ai.services;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.metadata.Caption;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;

/**
 * Implements {@link CaptionService} to transform given captions into VTT
 */
public class CaptionServiceImpl implements CaptionService {

    private static final Logger log = LogManager.getLogger(CaptionServiceImpl.class);

    protected static final String CAPTIONS_PREFIX = "captions-";

    protected static final String CAPTIONS_SUFFIX = ".vtt";

    protected static final String TIME_FORMAT = "HH:mm.ss.SSS";

    protected static final byte[] NO_CAPTIONS_COMMENT = "NOTE no captions available\n".getBytes();

    protected static final byte[] WEBVTT_HEADER = "WEBVTT\n\n".getBytes();

    public static final String TEXT_VTT_MIME_TYPE = "text/vtt";

    @Override
    public Blob write(@Nonnull List<Caption> captions) {
        try {
            File tempFile = Framework.createTempFile(CAPTIONS_PREFIX, CAPTIONS_SUFFIX);
            Framework.trackFile(tempFile, captions);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(WEBVTT_HEADER);

                if (captions.isEmpty()) {
                    fos.write(NO_CAPTIONS_COMMENT);
                }

                for (Caption cap : captions) {
                    String timestamp = getTimestamp(cap);
                    fos.write(timestamp.getBytes());

                    for (String l : cap.getLines()) {
                        fos.write(("- " + l + "\n").getBytes());
                    }

                    fos.write("\n".getBytes());
                }
            }

            return Blobs.createBlob(tempFile, TEXT_VTT_MIME_TYPE);
        } catch (IOException e) {
            log.error(e);
            throw new NuxeoException(e);
        }

    }

    protected String getTimestamp(Caption cap) {
        String start = DurationFormatUtils.formatDuration(cap.getStart(), TIME_FORMAT);
        String end = DurationFormatUtils.formatDuration(cap.getEnd(), TIME_FORMAT);

        return start + " --> " + end + "\n";
    }
}
