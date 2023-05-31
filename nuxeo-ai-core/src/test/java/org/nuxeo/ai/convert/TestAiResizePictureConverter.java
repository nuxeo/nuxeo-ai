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
import static org.nuxeo.ai.enrichment.EnrichmentUtils.DEFAULT_CONVERSATION_FORMAT;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.DEFAULT_CONVERTER;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.DEFAULT_IMAGE_DEPTH;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.DEFAULT_IMAGE_HEIGHT;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.DEFAULT_IMAGE_WIDTH;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
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
import org.nuxeo.ecm.platform.picture.api.ImagingConvertConstants;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy("org.nuxeo.ecm.platform.picture.api")
@Deploy("org.nuxeo.ecm.platform.picture.core")
@Deploy("org.nuxeo.ecm.platform.picture.convert")
@Deploy("org.nuxeo.ecm.platform.video.core")
@Deploy("org.nuxeo.ai.ai-core")
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestAiResizePictureConverter {

    @Inject
    protected ConversionService conversionService;

    @Test
    public void shouldConvertBlobWithAIConverter() throws IOException {
        File planeImg = FileUtils.getResourceFileFromContext("files/plane.jpg");
        Blob planeBlob = Blobs.createBlob(planeImg);
        planeBlob.setMimeType("image/jpeg");

        SimpleBlobHolder bh = new SimpleBlobHolder(planeBlob);
        Map<String, Serializable> parameters = new HashMap<>();
        parameters.put(ImagingConvertConstants.OPTION_RESIZE_WIDTH, DEFAULT_IMAGE_WIDTH);
        parameters.put(ImagingConvertConstants.OPTION_RESIZE_HEIGHT, DEFAULT_IMAGE_HEIGHT);
        parameters.put(ImagingConvertConstants.OPTION_RESIZE_DEPTH, DEFAULT_IMAGE_DEPTH);
        parameters.put(ImagingConvertConstants.CONVERSION_FORMAT, DEFAULT_CONVERSATION_FORMAT);

        BlobHolder res = conversionService.convert(DEFAULT_CONVERTER, bh, parameters);
        assertNotNull(res);
        Blob resBlob = res.getBlob();
        assertThat(resBlob.getMimeType()).isEqualTo("image/jpeg");
        assertThat(resBlob.getLength()).isNotZero();

        File planePNG = FileUtils.getResourceFileFromContext("files/plane.png");
        Blob planeBlobPNG = Blobs.createBlob(planePNG);
        planeBlobPNG.setMimeType("image/png");

        SimpleBlobHolder bhPNG = new SimpleBlobHolder(planeBlobPNG);
        res = conversionService.convert(DEFAULT_CONVERTER, bhPNG, parameters);
        assertNotNull(res);
        resBlob = res.getBlob();
        assertThat(resBlob.getMimeType()).isEqualTo("image/jpeg");
        assertThat(resBlob.getLength()).isNotZero();
    }

    @Test
    public void shouldSkipNotImageBlob() throws IOException {
        File htmlFile = FileUtils.getResourceFileFromContext("files/htmlTensor.html");
        Blob planeBlob = Blobs.createBlob(htmlFile);
        planeBlob.setMimeType("image/html");

        SimpleBlobHolder bh = new SimpleBlobHolder(planeBlob);
        Map<String, Serializable> parameters = new HashMap<>();
        parameters.put(ImagingConvertConstants.OPTION_RESIZE_WIDTH, DEFAULT_IMAGE_WIDTH);
        parameters.put(ImagingConvertConstants.OPTION_RESIZE_HEIGHT, DEFAULT_IMAGE_HEIGHT);
        parameters.put(ImagingConvertConstants.OPTION_RESIZE_DEPTH, DEFAULT_IMAGE_DEPTH);
        parameters.put(ImagingConvertConstants.CONVERSION_FORMAT, DEFAULT_CONVERSATION_FORMAT);

        BlobHolder res = conversionService.convert(DEFAULT_CONVERTER, bh, parameters);
        assertThat(res).isNotNull();
        assertThat(res.getBlob()).isNull();
        assertThat(res.getBlobs()).isEmpty();
    }
}
