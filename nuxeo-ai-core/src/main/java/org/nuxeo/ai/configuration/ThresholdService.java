/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Andrei Nechaev
 */
package org.nuxeo.ai.configuration;

import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Configuration done on XML as an extension
 * A global threshold for all properties
 * We can define different thresholds to different properties
 * In case of collision in configuration thresholds will be merged in favor of bigger values
 */
public interface ThresholdService {

    /**
     * Provides type/facet based threshold value from {@link ThresholdConfiguratorDescriptor}
     * @param doc {@link DocumentModel}'s type or facet to be used for finding threshold value
     * @param xpath of a property
     * @return if no contribution made returns confidence; if a global confidence level given and no property threshold
     * global is used
     */
    float getThreshold(DocumentModel doc, String xpath);

    /**
     * Provides autofill type/facet based threshold value from {@link ThresholdConfiguratorDescriptor}
     * @param doc {@link DocumentModel}'s type or facet to be used for finding threshold value
     * @param xpath of a property
     * @return if no contribution made returns confidence; if a global confidence level given and no property threshold
     * global is used
     */
    float getAutoFillThreshold(DocumentModel doc, String xpath);

    /**
     * Provides autocorrect type/facet based threshold value from {@link ThresholdConfiguratorDescriptor}
     * @param doc {@link DocumentModel}'s type or facet to be used for finding threshold value
     * @param xpath of a property
     * @return if no contribution made returns confidence; if a global confidence level given and no property threshold
     * global is used
     */
    float getAutoCorrectThreshold(DocumentModel doc, String xpath);
}
