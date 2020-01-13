/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.enrichment.async;

import static org.nuxeo.ai.transcribe.TranscribeWork.DEFAULT_CONVERSION;
import static org.nuxeo.ecm.platform.video.VideoConstants.VIDEO_FACET;

import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.enrichment.AbstractEnrichmentProvider;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.transcribe.TranscribeWork;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.video.TranscodedVideo;
import org.nuxeo.ecm.platform.video.VideoDocument;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

public class TranscribeEnrichmentProvider extends AbstractEnrichmentProvider {

    private static final Logger log = LogManager.getLogger(TranscribeEnrichmentProvider.class);

    public static final String PROVIDER_NAME = "aws.transcribe";

    public static final String PROVIDER_KIND = "/tagging/transcribe";

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobDoc) {
        if (!VIDEO_FACET.equals(blobDoc.getPrimaryType()) && !blobDoc.getFacets().contains(VIDEO_FACET)) {
            return null;
        }

        TransactionHelper.runInTransaction(
                () -> CoreInstance.doPrivileged(blobDoc.getRepositoryName(), session -> {
                    DocumentModel doc = session.getDocument(new IdRef(blobDoc.getId()));
                    VideoDocument video = doc.getAdapter(VideoDocument.class);
                    TranscodedVideo tv = video.getTranscodedVideo(DEFAULT_CONVERSION);
                    if (tv == null) {
                        log.warn("WAV is not ready; doc id = " + doc.getId());
                        return;
                    }

                    TranscribeWork work = new TranscribeWork(doc.getRepositoryName(), doc.getId());
                    Framework.getService(WorkManager.class).schedule(work);
                })
        );

        return null;
    }
}
