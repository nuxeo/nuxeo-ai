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
package org.nuxeo.ai.functions;

import java.util.Set;

import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentUtils;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ai.metadata.TagSuggestion;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.platform.tag.TagService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * A stream processor that saves enrichment labels as tags.
 */
public class StoreLabelsAsTags extends AbstractEnrichmentConsumer {

    @Override
    public void accept(EnrichmentMetadata metadata) {
        TransactionHelper.runInTransaction(() -> CoreInstance.doPrivileged(metadata.context.repositoryName, session -> {
            TagService tagService = Framework.getService(TagService.class);
            for (LabelSuggestion ls : metadata.getLabels()) {
                ls.getValues().forEach(l -> tagService.tag(session, metadata.context.documentRef, l.getName()));
            }
            for (TagSuggestion ts : metadata.getTags()) {
                Set<String> tags = EnrichmentUtils.getTagLabels(ts.getValues());
                tags.forEach(t -> tagService.tag(session, metadata.context.documentRef, t));
            }
        }));
    }
}
