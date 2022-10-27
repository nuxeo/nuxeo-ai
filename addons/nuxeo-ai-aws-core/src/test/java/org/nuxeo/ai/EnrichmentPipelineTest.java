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

package org.nuxeo.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_ITEMS_PROP;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.FILE_CONTENT;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.enrichment.TestConfiguredStreamProcessors;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.platform.picture.core.ImagingFeature;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lib.stream.log.LogLag;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.lib.stream.log.Name;
import org.nuxeo.runtime.stream.StreamService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

@RunWith(FeaturesRunner.class)
@Features({ EnrichmentTestFeature.class, PlatformFeature.class, AutomationFeature.class, ImagingFeature.class })
@Deploy("org.nuxeo.ecm.platform.commandline.executor")
@Deploy("org.nuxeo.ecm.actions")
@Deploy("org.nuxeo.ecm.platform.picture.core")
@Deploy("org.nuxeo.ecm.platform.rendition.api")
@Deploy("org.nuxeo.ecm.platform.rendition.core")
@Deploy("org.nuxeo.ecm.platform.video")
@Deploy("org.nuxeo.ecm.platform.tag")
@Deploy("org.nuxeo.ai.aws.aws-core")
@Deploy("org.nuxeo.ai.aws.aws-core:OSGI-INF/stream-pipes-test.xml")
public class EnrichmentPipelineTest {

    private static final Name TEST_IMAGES = Name.ofUrn("test/images");

    private static final Name TEST_IMAGES_OUT = Name.ofUrn("test/enrichment-in");

    private static final Name TEST_IMAGES_SAVE_GROUP = Name.ofUrn("ai/SaveEnrichmentFunction_test-enrichment-in");

    private static final Name TEST_IMAGES_GROUP = Name.ofUrn("ai/test-imageLabels_test-images_test-enrichment-in");

    @Inject
    protected CoreSession session;

    @Inject
    protected EventService eventService;

    @Inject
    protected TransactionalFeature txf;

    @Inject
    protected StreamService ss;

    @Test
    public void shouldRunEnrichmentsOnConversionDone() throws IOException, InterruptedException {
        //        AWS.assumeCredentials();
        File pic = FileUtils.getResourceFileFromContext("files/creative_commons3.jpg");
        Blob blob = Blobs.createBlob(pic);

        DocumentModel doc = session.createDocumentModel("/", "Test Doc", "Picture");
        doc.setPropertyValue(FILE_CONTENT, (Serializable) blob);
        doc = session.createDocument(doc);

        txf.nextTransaction();
        eventService.waitForAsyncCompletion();

        LogManager manager = ss.getLogManager();
        TestConfiguredStreamProcessors.waitForNoLag(manager, TEST_IMAGES_OUT, TEST_IMAGES_SAVE_GROUP,
                Duration.ofSeconds(10));

        LogLag lag = manager.getLag(TEST_IMAGES, TEST_IMAGES_GROUP);
        assertThat(lag.lag()).isZero();

        lag = manager.getLag(TEST_IMAGES_OUT, TEST_IMAGES_SAVE_GROUP);
        assertThat(lag.lag()).isZero();

        doc = session.getDocument(doc.getRef());
        Serializable views = doc.getPropertyValue("picture:views");
        assertThat(views).isNotNull();
        assertThat(doc.getFacets()).contains(ENRICHMENT_FACET);

        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) doc.getPropertyValue(ENRICHMENT_ITEMS_PROP);
        assertThat(items).isNotEmpty();
    }
}
