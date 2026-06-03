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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
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
 * Consumes {@link EnrichmentMetadata} produced by {@link ContentIntelligenceEnrichmentProvider} and tags the source
 * document with every label value returned by Hyland CI.
 * <p>
 * Long-form description / summary text ({@code imageDescription}, {@code textSummary}) is NEVER part of the labels
 * stream and is therefore never tagged here. It is persisted to {@code dc:description} by
 * {@link ContentIntelligenceDescriptionListener} from the raw Hyland CI JSON blob.
 */
public class StoreContentIntelligenceMetadata extends AbstractEnrichmentConsumer {

    /**
     * Defensive filter: if for any reason a description-bearing suggestion ever shows up in the labels stream, drop
     * it here instead of tagging the document with the full description paragraph.
     */
    public static final Set<String> DESCRIPTION_ACTIONS = ContentIntelligenceConstants.DESCRIPTION_ACTION_KEYS;

    /** Characters stripped out of tag names to keep them compatible with the Nuxeo {@link TagService}. */
    protected static final Pattern TAG_SANITIZER = Pattern.compile("[/'\\\\%]");

    /** Runs of whitespace in tag values are collapsed to a single hyphen so multi-word entities stay readable. */
    protected static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Trims leading and trailing hyphens introduced by sanitisation. */
    protected static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");

    /** Hyphens pattern for normalization during deduplication. */
    protected static final Pattern HYPHENS = Pattern.compile("-+");

    private static final Logger log = LogManager.getLogger(StoreContentIntelligenceMetadata.class);

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
            applyTags(session, doc, metadata);
        }));
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

    /**
     * Collects every taggable label value out of {@code metadata}. Suggestions whose
     * {@link LabelSuggestion#getProperty() property} matches a {@link #DESCRIPTION_ACTIONS description action} are
     * skipped wholesale; the remaining values are run through {@link #sanitizeTag(String)} and deduplicated.
     * <p>
     * Deduplication is performed using a normalized key (lowercase, hyphens removed) so that near-duplicates like
     * "high-contrast" and "highcontrast" collapse into a single tag. The first encountered version is kept.
     */
    protected Set<String> collectTagValues(EnrichmentMetadata metadata) {
        if (metadata.getLabels() == null || metadata.getLabels().isEmpty()) {
            return Set.of();
        }
        Map<String, String> tagsByNormalizedKey = new LinkedHashMap<>();
        for (LabelSuggestion suggestion : metadata.getLabels()) {
            if (suggestion == null || suggestion.getValues() == null
                    || DESCRIPTION_ACTIONS.contains(suggestion.getProperty())) {
                continue;
            }
            for (AIMetadata.Label label : suggestion.getValues()) {
                if (label == null || StringUtils.isBlank(label.getName())) {
                    continue;
                }
                String sanitized = sanitizeTag(label.getName());
                if (StringUtils.isNotBlank(sanitized)) {
                    String normalizedKey = normalizeForDedup(sanitized);
                    tagsByNormalizedKey.putIfAbsent(normalizedKey, sanitized);
                }
            }
        }
        return new LinkedHashSet<>(tagsByNormalizedKey.values());
    }

    /**
     * Normalizes a tag for deduplication: lowercase + remove hyphens. This ensures "high-contrast" and "highcontrast"
     * are treated as the same tag.
     */
    protected String normalizeForDedup(String tag) {
        return HYPHENS.matcher(tag.toLowerCase()).replaceAll("");
    }

    protected String sanitizeTag(String rawTag) {
        if (StringUtils.isBlank(rawTag)) {
            return null;
        }
        String collapsed = WHITESPACE.matcher(rawTag.trim()).replaceAll("-");
        String stripped = TAG_SANITIZER.matcher(collapsed).replaceAll("");
        String trimmed = EDGE_HYPHENS.matcher(stripped).replaceAll("");
        return StringUtils.isBlank(trimmed) ? null : trimmed;
    }
}
