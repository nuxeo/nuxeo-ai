package org.nuxeo.ai.pipes.filters;

import org.nuxeo.ai.pipes.streams.Initializable;
import org.nuxeo.ecm.core.api.DocumentModel;

import java.util.List;
import java.util.Map;

public class NoVersionFilter implements Filter.DocumentFilter, Initializable {

    protected List<String> facets;

    protected boolean excluded;

    @Override
    public void init(Map<String, String> options) {
        /* NOP */
    }

    /**
     * A predicate that check that none match the excluded facets and ANY match the included facets
     */
    @Override
    public boolean test(DocumentModel doc) {
        return !doc.isVersion();
    }

}
