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

package org.nuxeo.ai.similar.content.services;

import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Service responsible for configuration of Similarity search
 */
public interface SimilarContentService {

    /**
     * Tests if given Document is allowed by given configuration
     *
     * @param config {@link String} configuration name; see {@link org.nuxeo.ai.similar.content.configuration.DeduplicationDescriptor}
     * @param doc    {@link DocumentModel} Document to test
     * @return true if the Document allowed, false otherwise
     */
    boolean test(String config, DocumentModel doc);

    /**
     * Tests given Document against all available configurations; see {@link org.nuxeo.ai.similar.content.configuration.DeduplicationDescriptor}
     *
     * @param doc {@link DocumentModel} Document to test
     * @return true if the Document allowed, false otherwise
     */
    boolean anyMatch(DocumentModel doc);

    /**
     * Gets a query from the specified configuration; see {@link org.nuxeo.ai.similar.content.configuration.DeduplicationDescriptor}
     *
     * @param name {@link String} configuration name
     * @return {@link String} as an NXQL query
     */
    String getQuery(String name);

    /**
     * Get XPath from the specified configuration; see {@link org.nuxeo.ai.similar.content.configuration.DeduplicationDescriptor}
     *
     * @param name {@link String} configuration name
     * @return {@link String} as XPath
     */
    String getXPath(String name);
}
