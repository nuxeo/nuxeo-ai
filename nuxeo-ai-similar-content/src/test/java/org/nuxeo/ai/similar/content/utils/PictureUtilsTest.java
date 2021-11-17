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

package org.nuxeo.ai.similar.content.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nuxeo.ai.similar.content.utils.PictureUtils.HEADER_OFFSET;

import java.io.File;
import java.io.IOException;
import org.apache.commons.codec.binary.Base64;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ AutomationFeature.class })
@Deploy("org.nuxeo.ecm.platform.picture.core")
@Deploy("org.nuxeo.ecm.platform.picture.convert")
@Deploy("org.nuxeo.ecm.platform.picture.api")
@Deploy("org.nuxeo.ai.ai-core")
public class PictureUtilsTest {

    @Test
    public void shouldResizeSmall() throws IOException {
        File file = FileUtils.getResourceFileFromContext("files/ALASKA.jpeg");
        assertThat(file).isNotNull();

        Blob simple = Blobs.createBlob(file);
        String resized = PictureUtils.resize(simple);
        assertThat(resized).isNotBlank();
        byte[] decodedBuf = Base64.decodeBase64(resized);
        Blob decoded = Blobs.createBlob(decodedBuf);
        assertThat(decoded.getLength()).isEqualTo(simple.getLength());
    }

    @Test
    public void shouldResizeLarge() throws IOException {
        File file = FileUtils.getResourceFileFromContext("files/ALASKA.jpeg");
        assertThat(file).isNotNull();

        Blob large = Blobs.createBlob(file);
        large.setMimeType("image/jpeg");
        long maxSize = large.getLength() / 2;
        String resized = PictureUtils.resize(large, maxSize);

        assertThat(resized).isNotBlank();
        byte[] decodedBuf = Base64.decodeBase64(resized);
        Blob decoded = Blobs.createBlob(decodedBuf);
        assertThat(decoded.getLength()).isBetween(maxSize - HEADER_OFFSET, maxSize);
    }
}
