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

import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Java6Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.AWS;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import software.amazon.awssdk.services.transcribestreaming.model.Alternative;
import software.amazon.awssdk.services.transcribestreaming.model.LanguageCode;

@RunWith(FeaturesRunner.class)
@Features({PlatformFeature.class})
@Deploy("org.nuxeo.ai.aws.aws-core")
@Deploy("org.nuxeo.runtime.aws")
public class TestTranscribeService {

    @Inject
    protected TranscribeService ts;

    @Test
    public void shouldTranscribeVideo() throws IOException {
        AWS.assumeCredentials();

        File wav = FileUtils.getResourceFileFromContext("files/audio_short.wav");
        assertNotNull(wav);

        CompletableFuture<List<Alternative>> future = ts.transcribe(Blobs.createBlob(wav), LanguageCode.EN_US);
        List<Alternative> items = future.join();
        assertThat(items).isNotEmpty().hasSize(6);
    }
}
