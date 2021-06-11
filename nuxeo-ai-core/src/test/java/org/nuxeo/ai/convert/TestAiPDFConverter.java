/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.convert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.nuxeo.ai.convert.AiPDFConverter.AI_PDF_CONVERTER;
import static org.nuxeo.ai.convert.AiPDFConverter.PDF_MIME_TYPE;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy("org.nuxeo.ai.ai-core")
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestAiPDFConverter {

    @Inject
    protected ConversionService cs;

    @Test
    public void shouldConvertPDFWithCustomAIConverter() throws IOException {
        File pdf = FileUtils.getResourceFileFromContext("files/MLLecture1.pdf");
        assertNotNull(pdf);

        Blob blobPDF = Blobs.createBlob(pdf, PDF_MIME_TYPE);
        BlobHolder bh = new SimpleBlobHolder(blobPDF);

        BlobHolder result = cs.convert(AI_PDF_CONVERTER, bh, Collections.emptyMap());
        assertNotNull(result);
        assertThat(result.getBlob().getString()).startsWith("See discussions, stats, and author profiles");
    }
}
