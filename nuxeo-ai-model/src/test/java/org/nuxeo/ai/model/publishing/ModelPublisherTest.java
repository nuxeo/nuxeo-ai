/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     Pedro Cardoso
 */
package org.nuxeo.ai.model.publishing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.nuxeo.common.Environment;
import org.nuxeo.ecm.core.api.DocumentModel;

public class ModelPublisherTest implements ModelPublisherExtension {

    public static final int MODEL_VERSION = 10;

    protected String servingPath;

    protected String modelFileName;

    @Override
    public void init(Map<String, String> options) {
        servingPath = options.getOrDefault("servingPath", Environment.getDefault().getTemp().toString());
        modelFileName = options.getOrDefault("modelFilename", "MODEL_FILE");
    }

    @Override
    public void publishModel(String aiModelDocumentId) throws IOException {
        Path storageFolder = Paths.get(servingPath, String.valueOf(MODEL_VERSION));
        if (Files.exists(storageFolder)) {
            throw new IOException("model folder already exists. It is probably already published");
        }
        Files.createDirectories(storageFolder);
        Files.createFile(storageFolder.resolve(modelFileName));
    }

    @Override
    public void unpublishModel(String aiModelDocumentId) throws IOException {
        Path storageFolder = Paths.get(servingPath, String.valueOf(MODEL_VERSION));
        if (!Files.exists(storageFolder)) {
            throw new IOException("model folder does not exist. Not published");
        }
        FileUtils.deleteDirectory(storageFolder.toFile());
    }

    @Override
    public boolean isModelPublished(String aiModelDocumentId) {
        Path storageFolder = Paths.get(servingPath, String.valueOf(MODEL_VERSION));
        return Files.exists(storageFolder);
    }
}
