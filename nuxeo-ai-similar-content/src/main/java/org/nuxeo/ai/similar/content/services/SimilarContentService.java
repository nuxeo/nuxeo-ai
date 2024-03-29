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

import java.io.IOException;
import java.util.List;
import org.nuxeo.ai.bulk.BulkProgressStatus;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
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
     * @return name of a Nuxeo Operation that shall be used for deduplication resolution
     */
    String getOperationID();

    /**
     * Index repository
     *
     * @param session {@link CoreSession} as user's Session
     * @param query   {@link String} NXQL query to use
     * @param reindex boolean flag, <b>true</b> to reindex the entire repository based on the given query,
     *                false to exclude already indexed documents
     * @return {@link String} as BAF ID to track the progress
     */
    String index(CoreSession session, String query, boolean reindex) throws IOException;

    /**
     * Get the latest status of the index BAF task
     *
     * @return {@link BulkProgressStatus} of the task or null
     */
    BulkProgressStatus getStatus();

    /**
     * Get status of the index BAF task based on id
     *
     * @param id {@link String} ID of BAF task
     * @return {@link BulkProgressStatus} of the task or null
     */
    BulkProgressStatus getStatus(String id);

    /**
     * Send given {@link DocumentModel} for indexing
     *
     * @param doc   {@link DocumentModel} document to index
     * @param xpath {@link String} xpath to use for indexing
     * @return boolean value, true if success, false otherwise
     * @throws IOException in case of processing issues
     */
    boolean index(DocumentModel doc, String xpath) throws IOException;

    /**
     * Find Similar {@link DocumentModel}[s] for the provided {@link Blob}
     *
     * @param session {@link CoreSession} for obtaining user and repository info
     * @param doc     {@link DocumentModel} based on which similar search will run
     * @param xpath   {@link String} xpath of the indexed blob in {@link DocumentModel}
     * @return list of {@link DocumentModel} that are similar to the given {@link DocumentModel}
     * @throws IOException in case of processing issues
     */
    List<DocumentModel> findSimilar(CoreSession session, DocumentModel doc, String xpath) throws IOException;

    /**
     * Find Similar {@link DocumentModel}[s] for the provided {@link Blob}
     *
     * @param session {@link CoreSession} for obtaining user and repository info
     * @param doc     {@link DocumentModel} based on which similar search will run
     * @param xpath   {@link String} xpath of the indexed blob in {@link DocumentModel}
     * @param distance {@link Integer} max distance between entries.
     * @return list of {@link DocumentModel} that are similar to the given {@link DocumentModel}
     * @throws IOException in case of processing issues
     */
    List<DocumentModel> findSimilar(CoreSession session, DocumentModel doc, String xpath, int distance) throws IOException;

    /**
     * Find Similar {@link DocumentModel}[s] for the provided {@link Blob}
     *
     * @param session {@link CoreSession} for obtaining user and repository info
     * @param blob    {@link Blob} based on which similar search will run
     * @param xpath   {@link String} xpath of the indexed {@link Blob}
     * @param distance {@link Integer} max distance between entries.
     * @return list of {@link DocumentModel} that are similar to the given {@link Blob}
     * @throws IOException in case of processing issues
     */
    List<DocumentModel> findSimilar(CoreSession session, Blob blob, String xpath, int distance) throws IOException;

    /**
     * Find Similar {@link DocumentModel}[s] for the provided {@link Blob}
     *
     * @param session {@link CoreSession} for obtaining user and repository info
     * @param blob    {@link Blob} based on which similar search will run
     * @param xpath   {@link String} xpath of the indexed {@link Blob}
     * @return list of {@link DocumentModel} that are similar to the given {@link Blob}
     * @throws IOException in case of processing issues
     */
    List<DocumentModel> findSimilar(CoreSession session, Blob blob, String xpath) throws IOException;

    /**
     * Delete given document from the Insight index
     *
     * @param doc   {@link DocumentModel} document to remove
     * @param xpath {@link String} xpath of the index to remove
     * @return boolean value, true if success, false otherwise
     * @throws IOException in case of processing issues
     */
    boolean delete(DocumentModel doc, String xpath) throws IOException;

    boolean drop(CoreSession session) throws IOException;

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
