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
package org.nuxeo.ai.services;

import java.io.Serializable;
import java.util.List;
import org.nuxeo.ai.auto.AutoHistory;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Works with metadata for the Enrichment facet.
 */
public interface DocMetadataService {

    /**
     * Saves the enrichment metadata on a document and returns the DocumentModel.
     */
    DocumentModel saveEnrichment(CoreSession session, EnrichmentMetadata metadata);

    /**
     * Sets the document properties for autofill/auto correct and history information.
     */
    DocumentModel updateAuto(DocumentModel doc, String autoType, String xPath, Serializable oldValue, String comment);

    /**
     * Reset an auto field to its previous value.
     */
    DocumentModel resetAuto(DocumentModel doc, String autoType, String xPath, boolean resetValue);

    /**
     * Removes any suggestions for the specified output property.
     */
    DocumentModel removeSuggestionsForTargetProperty(DocumentModel doc, String xPath);

    /**
     * Gets the auto correct history for a document.
     */
    List<AutoHistory> getAutoHistory(DocumentModel doc);

    /**
     * Sets the auto correct history for a document.
     */
    void setAutoHistory(DocumentModel doc, List<AutoHistory> history);
}
