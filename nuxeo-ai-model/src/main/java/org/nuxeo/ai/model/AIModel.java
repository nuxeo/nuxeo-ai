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
 *     Gethin James
 */
package org.nuxeo.ai.model;

import java.util.Map;

/**
 * An Artificial Intelligence model
 */
public interface AIModel {

    String MODEL_NAME = "modelName";

    String MODEL_VERSION = "modelVersion";

    /**
     * Get the unique identifier for this model
     */
    String getId();

    /**
     * Any details about this model, used for information purposes
     */
    Map<String, String> getDetails();

    /**
     * Get the model name
     */
    default String getName() {
        return getDetails().get(MODEL_NAME);
    }

    /**
     * Get the model version
     */
    default String getVersion() {
        return getDetails().get(MODEL_VERSION);
    }
}
