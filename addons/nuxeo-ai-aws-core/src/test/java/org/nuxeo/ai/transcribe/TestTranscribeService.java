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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.fasterxml.jackson.databind.ObjectMapper;

@RunWith(FeaturesRunner.class)
@Features({PlatformFeature.class})
@Deploy("org.nuxeo.ai.aws.aws-core")
@Deploy("org.nuxeo.runtime.aws")
public class TestTranscribeService {

    protected String json = "{\n" +
            "    \"jobName\": \"EnUS_033178297efa75a1b0add2acaea8639e\",\n" +
            "    \"accountId\": \"783725821734\",\n" +
            "    \"results\": {\n" +
            "        \"transcripts\": [\n" +
            "            {\n" +
            "                \"transcript\": \"this guy. I really familiar. He was a long time.\"\n" +
            "            }\n" +
            "        ],\n" +
            "        \"items\": [\n" +
            "            {\n" +
            "                \"start_time\": \"0.62\",\n" +
            "                \"end_time\": \"0.88\",\n" +
            "                \"alternatives\": [\n" +
            "                    {\n" +
            "                        \"confidence\": \"0.9542\",\n" +
            "                        \"content\": \"this\"\n" +
            "                    }\n" +
            "                ],\n" +
            "                \"type\": \"pronunciation\"\n" +
            "            },\n" +
            "            {\n" +
            "                \"start_time\": \"0.88\",\n" +
            "                \"end_time\": \"1.32\",\n" +
            "                \"alternatives\": [\n" +
            "                    {\n" +
            "                        \"confidence\": \"0.956\",\n" +
            "                        \"content\": \"guy\"\n" +
            "                    }\n" +
            "                ],\n" +
            "                \"type\": \"pronunciation\"\n" +
            "            }\n" +
            "        ]\n" +
            "    },\n" +
            "    \"status\": \"COMPLETED\"\n" +
            "}";

    @Inject
    protected TranscribeService ts;

    @Test
    public void shouldTranscribeVideo() throws IOException {
        ObjectMapper om = new ObjectMapper();
        AmazonTranscription transcription = om.readValue(json, AmazonTranscription.class);
        assertNotNull(transcription);
        assertEquals(2, transcription.results.items.size());
    }
}
