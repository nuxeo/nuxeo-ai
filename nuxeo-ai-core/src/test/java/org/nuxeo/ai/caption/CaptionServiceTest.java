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
package org.nuxeo.ai.caption;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.nuxeo.ai.services.CaptionServiceImpl.TEXT_VTT_MIME_TYPE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.metadata.Caption;
import org.nuxeo.ai.services.CaptionService;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy("org.nuxeo.ai.ai-core")
public class CaptionServiceTest {

    @Inject
    protected CaptionService cs;

    @Test
    public void shouldWriteCaptionsIntoBlobWithVTTMimeType() throws IOException {
        Caption caption = new Caption(0, 1000, Collections.emptyList());
        // used as a ref to track the temp file, so should be initialized out of scope of the method call
        List<Caption> captions = Collections.singletonList(caption);
        Blob blob = cs.write(captions);
        assertNotNull(blob);
        assertEquals(TEXT_VTT_MIME_TYPE, blob.getMimeType());

        File file = blob.getFile();
        assertThat(file).isNotNull().hasExtension("vtt");
        assertThat(file.length()).isNotZero();

        try (FileInputStream fis = new FileInputStream(file);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis, UTF_8))) {
            String[] lines = br.lines().toArray(String[]::new);
            assertThat(lines).hasSize(4);
            assertThat(lines[0]).isEqualTo("WEBVTT");
            assertThat(lines[2]).startsWith("00:00.00.000").endsWith("00:00.01.000");
        }
    }
}
