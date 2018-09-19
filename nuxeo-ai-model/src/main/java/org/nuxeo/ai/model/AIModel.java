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

import java.util.Set;

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
     * The input properties
     */
    Set<ModelProperty> getInputs();

    /**
     * The output properties
     */
    Set<ModelProperty> getOutputs();

    /**
     * Get the model name
     */
    String getName();

    /**
     * Get the model version
     */
    String getVersion();
}
