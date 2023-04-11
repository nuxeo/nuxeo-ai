/*
 * (C) Copyright 2006-2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 * Contributors:
 *     tiry
 *     anechaev
 *
 */
package org.nuxeo.ai.convert;

import org.nuxeo.ecm.platform.commandline.executor.api.CmdParameters;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandException;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandLineExecutorService;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandNotAvailable;
import org.nuxeo.ecm.platform.commandline.executor.api.ExecResult;
import org.nuxeo.ecm.platform.picture.api.ImageInfo;
import org.nuxeo.ecm.platform.picture.magick.MagickExecutor;
import org.nuxeo.ecm.platform.picture.magick.utils.ImageIdentifier;
import org.nuxeo.runtime.api.Framework;

import java.awt.Point;
import java.io.File;

import static org.nuxeo.ai.convert.AiResizePictureConverter.AI_JPEG_RESIZER_COMMAND;
import static org.nuxeo.ai.convert.AiResizePictureConverter.AI_RESIZER_COMMAND;
import static org.nuxeo.ecm.platform.picture.api.ImagingConvertConstants.JPEG_CONVERSATION_FORMAT;

/**
 * Unit command to extract a simplified view of a JPEG file using ImageMagick = extract the needed picture information
 * to reach the target definition level
 *
 * @since 2.2.0
 */
public class AiImageResizer extends MagickExecutor {

    public static final String TARGET_WIDTH_PARAM = "targetWidth";

    public static final String TARGET_HEIGHT_PARAM = "targetHeight";

    public static final String TARGET_DEPTH_PARAM = "targetDepth";

    public static final String TARGET_INPUT_FILE_PATH_PARAM = "inputFilePath";

    public static final String TARGET_OUTPUT_FILE_PATH_PARAM = "outputFilePath";

    public static final int MAX_JPEG_DIMENSION = 65500;

    public static ImageInfo resize(String inputFile, String outputFile, int targetWidth, int targetHeight,
                                   int targetDepth) throws CommandNotAvailable, CommandException {
        if (targetDepth == -1) {
            targetDepth = ImageIdentifier.getInfo(inputFile).getDepth();
        }
        CommandLineExecutorService cles = Framework.getService(CommandLineExecutorService.class);
        CmdParameters params = cles.getDefaultCmdParameters();
        params.addNamedParameter(TARGET_WIDTH_PARAM, String.valueOf(targetWidth));
        params.addNamedParameter(TARGET_HEIGHT_PARAM, String.valueOf(targetHeight));
        params.addNamedParameter(TARGET_INPUT_FILE_PATH_PARAM, inputFile);
        params.addNamedParameter(TARGET_OUTPUT_FILE_PATH_PARAM, outputFile);
        params.addNamedParameter(TARGET_DEPTH_PARAM, String.valueOf(targetDepth));
        String commandName = AI_RESIZER_COMMAND;
        // hack to manage jpeg default background
        if (outputFile.endsWith(JPEG_CONVERSATION_FORMAT)) {
            commandName = AI_JPEG_RESIZER_COMMAND;
            Point size = scaleToMax(targetWidth, targetHeight, MAX_JPEG_DIMENSION);
            params.addNamedParameter(TARGET_WIDTH_PARAM, String.valueOf(size.getX()));
            params.addNamedParameter(TARGET_HEIGHT_PARAM, String.valueOf(size.getY()));
        }

        ExecResult res = cles.execCommand(commandName, params);
        if (!res.isSuccessful()) {
            throw res.getError();
        }

        if (new File(outputFile).exists()) {
            return ImageIdentifier.getInfo(outputFile);
        } else {
            return null;
        }
    }

    /**
     * Adapts width and height to a max conserving ratio.
     *
     * @return new Point to scale or the original one if none is > max.
     * @since 2.2.0
     */
    public static Point scaleToMax(int width, int height, int max) {
        if (max > 0 && (width > max || height > max)) {
            float maxSide = Math.max(width, height);
            float ratio = maxSide / max;
            return new Point(Math.round(width / ratio), Math.round(height / ratio));
        }
        return new Point(width, height);
    }

}
