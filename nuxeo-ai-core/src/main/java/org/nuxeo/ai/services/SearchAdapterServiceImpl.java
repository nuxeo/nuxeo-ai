package org.nuxeo.ai.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.search.SearchQuery;
import org.nuxeo.ecm.core.search.SearchResponse;
import org.nuxeo.ecm.core.search.SearchService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;

/** Default implementation delegating to Nuxeo SearchService (core-search). */
public class SearchAdapterServiceImpl extends DefaultComponent implements SearchAdapterService {

    private static final Logger log = LogManager.getLogger(SearchAdapterServiceImpl.class);

    @Override
    public SearchSummary search(CoreSession session, String nxql, SearchOptions options) {
        try {
            SearchService ss = Framework.getService(SearchService.class);
            if (ss == null) {
                log.warn("SearchService unavailable, falling back to CoreSession.query for NXQL: {}", nxql);
                int lim = options.getLimit();
                if (lim > -1) {
                    DocumentModelList list = session.query(nxql, lim);
                    return SearchSummary.of(list.totalSize(), list.size());
                }
                DocumentModelList list = session.query(nxql);
                return SearchSummary.of(list.totalSize(), list.size());
            }
            String index = options.getIndex() != null ? options.getIndex() : DEFAULT_INDEX;
            SearchQuery query;
            if (options.getLimit() > -1) {
                query = SearchQuery.builder(nxql, session).index(index).limit(options.getLimit()).build();
            } else {
                query = SearchQuery.builder(nxql, session).index(index).build();
            }
            SearchResponse resp = ss.search(query);
            return SearchSummary.of(resp.getTotal(), resp.getHitsCount());
        } catch (Exception e) {
            log.error("Search failure, fallback to count only", e);
            DocumentModelList list = session.query(nxql);
            return SearchSummary.of(list.totalSize(), list.size());
        }
    }

    @Override
    public long count(CoreSession session, String nxql) {
        try {
            SearchService ss = Framework.getService(SearchService.class);
            if (ss == null) {
                return session.query(nxql).totalSize();
            }
            SearchQuery query = SearchQuery.builder(nxql, session).index(DEFAULT_INDEX).limit(0).build();
            SearchResponse resp = ss.search(query);
            return resp.getTotal();
        } catch (Exception e) {
            log.error("Count failure fallback", e);
            return session.query(nxql).totalSize();
        }
    }
}
