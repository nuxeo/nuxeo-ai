package org.nuxeo.ai.services;

/** Options controlling the adapter behaviour. */
public class SearchOptions {

    protected String index;
    protected int limit = -1; // -1 means default

    public String getIndex() {
        return index;
    }

    public int getLimit() {
        return limit;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        protected final SearchOptions opts = new SearchOptions();

        public Builder index(String index) {
            opts.index = index;
            return this;
        }

        public Builder limit(int limit) {
            opts.limit = limit;
            return this;
        }

        public SearchOptions build() {
            return opts;
        }
    }
}

