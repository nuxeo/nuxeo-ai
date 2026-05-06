/*
 * (C) Copyright 2026 Hyland (http://hyland.com/) and others.
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
 */
package org.nuxeo.ai.contentintelligence;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.functions.AbstractEnrichmentConsumer;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.platform.tag.TagService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Consumes {@link EnrichmentMetadata} produced by {@link ContentIntelligenceEnrichmentProvider} and writes its results
 * back onto the source document:
 * <ul>
 *   <li>Labels prefixed with {@code imageDescription/} (images) or {@code textSummary/} (PDFs / Office documents /
 *       plain text) populate {@code dc:description}.</li>
 *   <li>Every other non-description label returned by Hyland CI (e.g. {@code imageClassification/...},
 *       {@code textClassification/...}, NER entity buckets) is written as a document tag via the
 *       {@link TagService}.</li>
 * </ul>
 * Values keep only the portion after the first {@code /} (i.e. the raw action result) so descriptions read as
 * plain text and tags don't carry action-name prefixes.
 */
public class StoreContentIntelligenceMetadata extends AbstractEnrichmentConsumer {

    private static final Logger log = LogManager.getLogger(StoreContentIntelligenceMetadata.class);

    /** Action names whose value is treated as a plain description and written to {@code dc:description}. */
    public static final Set<String> DESCRIPTION_ACTIONS = Set.of("imageDescription", "textSummary");

    public static final String DESCRIPTION_PROPERTY = "dc:description";

    /** Characters stripped out of tag names to keep them compatible with the Nuxeo {@link TagService}. */
    protected static final Pattern TAG_SANITIZER = Pattern.compile("[/'\\\\%]");

    /** Runs of whitespace in tag values are collapsed to a single hyphen so multi-word entities stay readable. */
    protected static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @Override
    public void accept(EnrichmentMetadata metadata) {
        if (metadata == null || metadata.context == null || metadata.context.repositoryName == null
                || StringUtils.isBlank(metadata.context.documentRef)) {
            log.warn("Invalid enrichment metadata, skipping: {}", metadata);
            return;
        }

        TransactionHelper.runInTransaction(() -> CoreInstance.doPrivileged(metadata.context.repositoryName, session -> {
            DocumentModel doc;
            try {
                doc = session.getDocument(new IdRef(metadata.context.documentRef));
            } catch (DocumentNotFoundException e) {
                log.warn("Document {} no longer exists, dropping Content Intelligence metadata",
                        metadata.context.documentRef);
                return;
            }

            if (applyDescription(doc, metadata)) {
                session.saveDocument(doc);
            }
            applyTags(session, doc, metadata);
        }));
    }

    /**
     * Extracts the first non-blank description label value (from any {@link #DESCRIPTION_ACTIONS}) and writes it to
     * {@code dc:description}, but only if the property is currently empty, to avoid overwriting user-edited
     * descriptions.
     *
     * @return {@code true} if the document was mutated
     */
    protected boolean applyDescription(DocumentModel doc, EnrichmentMetadata metadata) {
        String description = firstValueForActions(metadata, DESCRIPTION_ACTIONS);
        if (StringUtils.isBlank(description)) {
            return false;
        }
        Object existing = doc.getPropertyValue(DESCRIPTION_PROPERTY);
        if (existing != null && StringUtils.isNotBlank(existing.toString())) {
            log.debug("dc:description already set on {}, skipping AI description", doc.getId());
            return false;
        }
        doc.setPropertyValue(DESCRIPTION_PROPERTY, description);
        return true;
    }

    /** Tags the document with every non-description label value returned by Hyland CI. */
    protected void applyTags(CoreSession session, DocumentModel doc, EnrichmentMetadata metadata) {
        Set<String> tags = collectTagValues(metadata);
        if (tags.isEmpty()) {
            return;
        }
        TagService tagService = Framework.getService(TagService.class);
        if (tagService == null) {
            log.warn("TagService not available, skipping tag creation for {}", doc.getId());
            return;
        }
        for (String tag : tags) {
            tagService.tag(session, doc.getId(), tag);
        }
    }

    protected Set<String> collectTagValues(EnrichmentMetadata metadata) {
        Set<String> tags = new LinkedHashSet<>();
        if (metadata.getLabels() == null) {
            return tags;
        }
        for (LabelSuggestion suggestion : metadata.getLabels()) {
            if (suggestion == null || suggestion.getValues() == null) {
                continue;
            }
            for (AIMetadata.Label label : suggestion.getValues()) {
                if (label == null || StringUtils.isBlank(label.getName())) {
                    continue;
                }
                String[] parts = splitActionValue(label.getName());
                if (DESCRIPTION_ACTIONS.contains(parts[0])) {
                    continue;
                }
                String sanitized = sanitizeTag(parts[1]);
                if (StringUtils.isNotBlank(sanitized)) {
                    tags.add(sanitized);
                }
            }
        }
        return tags;
    }

    protected String firstValueForActions(EnrichmentMetadata metadata, Set<String> actionNames) {
        if (metadata.getLabels() == null) {
            return null;
        }
        return metadata.getLabels()
                       .stream()
                       .filter(Objects::nonNull)
                       .flatMap(s -> s.getValues() == null ? java.util.stream.Stream.empty() : s.getValues().stream())
                       .filter(Objects::nonNull)
                       .map(AIMetadata.Label::getName)
                       .filter(StringUtils::isNotBlank)
                       .map(this::splitActionValue)
                       .filter(parts -> actionNames.contains(parts[0]) && StringUtils.isNotBlank(parts[1]))
                       .map(parts -> parts[1])
                       .findFirst()
                       .orElse(null);
    }

    /**
     * Splits a label name formatted as {@code actionName/value} into a two-element array. When no slash is present the
     * value is left blank and the whole name is treated as the action.
     */
    protected String[] splitActionValue(String labelName) {
        int idx = labelName.indexOf('/');
        if (idx < 0) {
            return new String[] { labelName, "" };
        }
        return new String[] { labelName.substring(0, idx), labelName.substring(idx + 1) };
    }

    protected String sanitizeTag(String rawTag) {
        if (StringUtils.isBlank(rawTag)) {
            return null;
        }
        String collapsed = WHITESPACE.matcher(rawTag.trim()).replaceAll("-");
        return TAG_SANITIZER.matcher(collapsed).replaceAll("");
    }
}
