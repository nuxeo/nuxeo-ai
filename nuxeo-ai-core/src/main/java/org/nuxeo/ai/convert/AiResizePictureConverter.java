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

import static org.nuxeo.ecm.platform.picture.api.ImagingConvertConstants.CONVERSION_FORMAT;
import static org.nuxeo.ecm.platform.picture.api.ImagingConvertConstants.OPTION_RESIZE_DEPTH;
import static org.nuxeo.ecm.platform.picture.api.ImagingConvertConstants.OPTION_RESIZE_HEIGHT;
import static org.nuxeo.ecm.platform.picture.api.ImagingConvertConstants.OPTION_RESIZE_WIDTH;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.core.convert.cache.SimpleCachableBlobHolder;
import org.nuxeo.ecm.core.convert.extension.Converter;
import org.nuxeo.ecm.core.convert.extension.ConverterDescriptor;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandException;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandNotAvailable;
import org.nuxeo.ecm.platform.picture.core.im.IMImageUtils;

/**
 * Image Converter optimized for AI purposes
 */
public class AiResizePictureConverter implements Converter {

    private static final Logger log = LogManager.getLogger(AiResizePictureConverter.class);

    public static final String AI_RESIZER_COMMAND = "aiResizer";

    public static final String AI_JPEG_RESIZER_COMMAND = "aiJpegResizer";

    @Override
    public BlobHolder convert(BlobHolder blobHolder, Map<String, Serializable> parameters) throws ConversionException {
        List<Blob> sources = blobHolder.getBlobs();
        List<Blob> results = new ArrayList<>(sources.size());

        Serializable h = parameters.get(OPTION_RESIZE_HEIGHT);
        int height = getInteger(h);
        Serializable w = parameters.get(OPTION_RESIZE_WIDTH);
        int width = getInteger(w);
        Serializable d = parameters.get(OPTION_RESIZE_DEPTH);
        int depth = getInteger(d);
        // use the registered conversion format
        String format = (String) parameters.get(CONVERSION_FORMAT);
        for (Blob source : sources) {
            if (source != null) {
                Blob result = new IMImageUtils.ImageMagickCaller() {
                    @Override
                    public void callImageMagick() throws CommandNotAvailable, CommandException {
                        AiImageResizer.resize(sourceFile.getAbsolutePath(), targetFile.getAbsolutePath(), width, height,
                                depth);
                    }
                }.call(source, format, AI_RESIZER_COMMAND);

                if (result != null) {
                    results.add(result);
                } else {
                    log.warn("Could not resize blob {} with digest {}", source.getFilename(), source.getDigest());
                }
            }
        }
        return new SimpleCachableBlobHolder(results);
    }

    @Override
    public void init(ConverterDescriptor descriptor) {
    }

    protected static int getInteger(Serializable value) {
        if (value instanceof Integer) {
            return (int) value;
        } else {
            return (value == null) ? 0 : Integer.parseInt(value.toString());
        }
    }
}
