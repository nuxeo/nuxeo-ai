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
import java.util.Map;

import org.nuxeo.ecm.core.api.DocumentModel;

public interface ModelPublisherExtension {

    /**
     * Publishes a model. Most common implementation is a publish into a filesystem Throws IOException if the model
     * location already exists, usually because the model is already published
     *
     * @param aiModelDocumentId A DocumentModel ID of the type Ai_Model, or extension, that contains the information on the
     *            model to publish.
     * @throws IOException throws an IOException if the publisher is using a file system and can't publish.
     */
    void publishModel(String aiModelDocumentId) throws IOException;

    /**
     * unpublishes a specific model, making it unavailable
     *
     * @param aiModelDocumentId A DocumentModel ID of the type Ai_Model, or extension, that contains the information on the
     *            model to unpublish.
     * @throws IOException throws an IOException if the publisher is using a file system and an error occur.
     */
    void unpublishModel(String aiModelDocumentId) throws IOException;

    /**
     * Checks if a particular model is already published.
     *
     * @param aiModelDocumentId A DocumentModel ID of the type Ai_Model, or extension, that contains the information on the
     *            model to check.
     * @return A boolean at true if the model is published, false otherwise.
     */
    boolean isModelPublished(String aiModelDocumentId);

    /**
     * Initialize the publisher using the supplied options
     */
    void init(Map<String, String> options);
}
