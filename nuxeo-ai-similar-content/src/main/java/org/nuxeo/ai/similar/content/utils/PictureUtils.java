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

import static org.nuxeo.ai.enrichment.EnrichmentUtils.DEFAULT_CONVERTER;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.base64EncodeBlob;

import org.nuxeo.ai.enrichment.EnrichmentUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.platform.picture.api.ImageInfo;
import org.nuxeo.ecm.platform.picture.api.ImagingService;
import org.nuxeo.runtime.api.Framework;

public final class PictureUtils {

    public static long MAX_SIZE_BYTES = 5 * 1024 * 1024;

    public static long HEADER_OFFSET = 80_000;

    private PictureUtils() {
    }

    public static String resize(Blob blob) {
        return resize(blob, MAX_SIZE_BYTES);
    }

    public static String resize(Blob blob, long max) {
        assert max > 0;
        if (blob != null) {
            long size = blob.getLength();
            if (size > max) {
                ImagingService is = Framework.getService(ImagingService.class);
                ImageInfo info = is.getImageInfo(blob);

                double sqrtRatio = Math.sqrt((max - HEADER_OFFSET) / (1.f * size));
                int width = (int) (info.getWidth() * sqrtRatio);
                int height = (int) (info.getHeight() * sqrtRatio);
                blob = EnrichmentUtils.convertImageBlob(DEFAULT_CONVERTER, blob, width, height, info.getDepth(),
                        info.getFormat());
            }

            return base64EncodeBlob(blob);
        }

        return null;
    }
}
