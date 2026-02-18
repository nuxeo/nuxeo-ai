package org.nuxeo.ai.services;

import org.nuxeo.ecm.core.api.CoreSession;

/**
 * Provides an abstraction over the underlying search implementation (Elasticsearch / OpenSearch / Core search).
 * Centralizes usage so that future migrations only require changes here.
 */
public interface SearchAdapterService {

    // Central default index name used across the codebase (previously hard-coded as "enhanced")
    String DEFAULT_INDEX = "enhanced";

    /**
     * Executes a search and returns a summary (total docs matched and hits returned).
     */
    SearchSummary search(CoreSession session, String nxql, SearchOptions options);

    /**
     * Convenience method using default options.
     */
    default SearchSummary search(CoreSession session, String nxql) {
        return search(session, nxql, SearchOptions.builder().build());
    }

    /**
     * Returns only the total number of matching documents (fast path when no hits are needed).
     */
    long count(CoreSession session, String nxql);
}
