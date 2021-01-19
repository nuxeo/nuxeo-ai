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

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentUtils;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.platform.tag.TagService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

import java.util.regex.Pattern;

/**
 * A stream processor that saves enrichment labels as tags.
 */
public class StoreLabelsAsTags extends AbstractEnrichmentConsumer {

    protected static final Pattern ALLOWED_PATTERN = Pattern.compile("[/'\\\\ %]");

    @Override
    public void accept(EnrichmentMetadata metadata) {
        TransactionHelper.runInTransaction(() -> CoreInstance.doPrivileged(metadata.context.repositoryName, session -> {
            TagService tagService = Framework.getService(TagService.class);
            metadata.getLabels()
                    .stream()
                    .flatMap(label -> label.getValues().stream())
                    .map(this::toTag)
                    .filter(StringUtils::isNotBlank)
                    .forEach(t -> tagService.tag(session, metadata.context.documentRef, t));
            metadata.getTags()
                    .stream()
                    .flatMap(tag -> EnrichmentUtils.getTagLabels(tag.getValues()).stream())
                    .map(this::toTag)
                    .filter(StringUtils::isNotBlank)
                    .forEach(t -> tagService.tag(session, metadata.context.documentRef, t));
        }));
    }

    protected String toTag(AIMetadata.Label label) {
        return ALLOWED_PATTERN.matcher(label.getName()).replaceAll("");
    }

    protected String toTag(String tag) {
        return ALLOWED_PATTERN.matcher(tag).replaceAll("");
    }
}
