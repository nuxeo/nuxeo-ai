package org.nuxeo.ai.services;

/** Simple summary of a search execution focusing on counts. */
public class SearchSummary {

    protected final long total;
    protected final long hitsCount;

    public SearchSummary(long total, long hitsCount) {
        this.total = total;
        this.hitsCount = hitsCount;
    }

    public static SearchSummary of(long total, long hitsCount) {
        return new SearchSummary(total, hitsCount);
    }

    public long getTotal() {
        return total;
    }

    public long getHitsCount() {
        return hitsCount;
    }
}

