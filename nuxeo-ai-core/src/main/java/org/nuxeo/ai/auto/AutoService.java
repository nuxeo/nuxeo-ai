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
 */
package org.nuxeo.ai.auto;

import org.nuxeo.ecm.core.api.DocumentModel;

import java.util.Map;

/**
 * Autofill and AutoCorrect services
 * This service deals with the logic of "auto properties", modifying the actual document enrichment facet is done by the
 * DocMetadataService.
 */
public interface AutoService {

    /**
     * Calculate all properties on a Document.
     */
    void calculateProperties(DocumentModel doc);

    /**
     * Calculate all properties on a Document, given the type of calculation.
     */
    void calculateProperties(DocumentModel doc, AUTO_ACTION action);

    /**
     * If a property has been modified then remove its auto information.
     */
    void autoApproveDirtyProperties(DocumentModel doc);

    /**
     * Remove the property from the list of auto updated values and remove suggestions.
     */
    void approveAutoProperty(DocumentModel doc, String xPath);

    /**
     * @return Suggestions, autofilled and autocorrect global metrics along with categories
     */
    Map<String, AutoServiceImpl.Metrics> getGlobalMetrics();

    AutoServiceImpl.TimeSeriesMetrics getPerformanceMetrics();

    enum AUTO_ACTION {
        FILL, CORRECT, ALL
    }
}
