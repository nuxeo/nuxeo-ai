/*
 *   (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *
 *   Contributors:
 *       anechaev
 */
package org.nuxeo.ai.model.serving;

import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.enrichment.AbstractEnrichmentProvider;
import org.nuxeo.ai.enrichment.EnrichmentCachable;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

import net.jodah.failsafe.RetryPolicy;

public class InsightProvider extends AbstractEnrichmentProvider implements EnrichmentCachable {

    private static final Logger log = LogManager.getLogger(InsightProvider.class);

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument btfd) {
        return TransactionHelper.runInTransaction(() -> CoreInstance.doPrivileged(btfd.getRepositoryName(), session -> {
            DocumentModel doc;
            try {
                doc = session.getDocument(new IdRef(btfd.getId()));
            } catch (DocumentNotFoundException e) {
                log.warn("Document {} no longer exists, skipping enrichment", btfd.getId());
                return Collections.emptyList();
            }

            ModelServingService mss = Framework.getService(ModelServingService.class);
            List<EnrichmentMetadata> metadata = mss.predict(doc);
            if (metadata == null || metadata.isEmpty()) {
                return Collections.emptyList();
            }

            return metadata;
        }));
    }

    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy().abortOn(NuxeoException.class);
    }
}
