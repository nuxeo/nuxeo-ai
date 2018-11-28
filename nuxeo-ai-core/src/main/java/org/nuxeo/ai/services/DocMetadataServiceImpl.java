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
package org.nuxeo.ai.services;

import static org.nuxeo.ai.AIConstants.AI_CREATOR_PROPERTY;
import static org.nuxeo.ai.AIConstants.AI_SERVICE_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_ITEMS;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_KIND_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_LABELS_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_RAW_KEY_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_SCHEMA_NAME;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_INPUT_DOCPROP_PROPERTY;
import static org.nuxeo.ai.AIConstants.NORMALIZED_PROPERTY;
import static org.nuxeo.ai.pipes.events.DirtyEventListener.DIRTY_EVENT_NAME;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.enrichment.EnrichedPropertiesEventListener;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.services.config.ConfigurationService;
import org.nuxeo.ai.pipes.services.PipelineService;

/**
 * An implementation of DocMetadataService
 */
public class DocMetadataServiceImpl extends DefaultComponent implements DocMetadataService {

    private static final Log log = LogFactory.getLog(DocMetadataServiceImpl.class);

    public static final String ENRICHMENT_ADDED = "ENRICHMENT_ADDED";

    public static final String ENRICHMENT_USING_FACETS = "nuxeo.enrichment.facets.inUse";

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        if (Framework.getService(ConfigurationService.class).isBooleanPropertyTrue(ENRICHMENT_USING_FACETS)) {
            // Facets are being used so lets clean it up as well.
            Framework.getService(PipelineService.class)
                     .addEventListener(DIRTY_EVENT_NAME, false, new EnrichedPropertiesEventListener());
        }
    }

    @Override
    public DocumentModel saveEnrichment(CoreSession session, EnrichmentMetadata metadata) {
        //TODO: Handle versions here?
        DocumentModel doc;
        try {
            doc = session.getDocument(new IdRef(metadata.context.documentRef));
        } catch (DocumentNotFoundException e) {
            log.info("Unable to save enrichment data for missing doc " + metadata.context.documentRef);
            return null;
        }
        if (!doc.hasFacet(ENRICHMENT_FACET)) {
            doc.addFacet(ENRICHMENT_FACET);
        }

        Map<String, Object> anItem = createEnrichment(metadata);

        if (anItem != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> enrichmentList = (List) doc.getProperty(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS);
            if (enrichmentList == null) {
                enrichmentList = new ArrayList<>(1);
            }
            enrichmentList.add(anItem);
            doc.setProperty(ENRICHMENT_SCHEMA_NAME, ENRICHMENT_ITEMS, enrichmentList);
            doc.putContextData(ENRICHMENT_ADDED, Boolean.TRUE);
        }
        return doc;
    }

    /**
     * Create a enrichment Map using the enrichment metadata
     */
    protected Map<String, Object> createEnrichment(EnrichmentMetadata metadata) {
        Map<String, Object> anEntry = new HashMap<>();
        AIComponent aiComponent = Framework.getService(AIComponent.class);

        Set<String> labels = new HashSet<>();
        labels.addAll(metadata.getLabels().stream()
                              .filter(Objects::nonNull)
                              .map(label -> label.getName().toLowerCase())
                              .distinct()
                              .collect(Collectors.toList()));
        labels.addAll(getTagLabels(metadata.getTags()));

        if (!labels.isEmpty()) {
            anEntry.put(ENRICHMENT_LABELS_PROPERTY, labels);

            Blob metaDataBlob;
            Blob rawBlob = null;
            try {
                if (StringUtils.isNotEmpty(metadata.getRawKey())) {
                    TransientStore transientStore = aiComponent
                            .getTransientStoreForEnrichmentService(metadata.getServiceName());
                    List<Blob> rawBlobs = transientStore.getBlobs(metadata.getRawKey());
                    if (rawBlobs != null && rawBlobs.size() == 1) {
                        rawBlob = rawBlobs.get(0);
                    } else {
                        log.warn(String.format("Unexpected transient store raw blob information for %s. " +
                                                       "A single raw blob is expected.", metadata.getServiceName()));
                    }
                }
                metaDataBlob = Blobs.createJSONBlob(MAPPER.writeValueAsString(metadata));
            } catch (IOException e) {
                throw new NuxeoException("Unable to process metadata blob", e);
            }

            anEntry.put(AI_SERVICE_PROPERTY, metadata.getServiceName());
            anEntry.put(ENRICHMENT_KIND_PROPERTY, metadata.getKind());
            anEntry.put(ENRICHMENT_INPUT_DOCPROP_PROPERTY, metadata.context.inputProperties);
            anEntry.put(ENRICHMENT_RAW_KEY_PROPERTY, rawBlob);
            anEntry.put(NORMALIZED_PROPERTY, metaDataBlob);
            if (StringUtils.isNotBlank(metadata.getCreator())) {
                anEntry.put(AI_CREATOR_PROPERTY, metadata.getCreator());
            }
            if (log.isDebugEnabled()) {
                log.debug(String.format("Enriching doc %s with %s", metadata.context.documentRef, labels));
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug(String.format("No labels for %s so not saving any enrichment data",
                                        metadata.context.documentRef
                ));
            }
            return null;
        }

        return anEntry;
    }

    /**
     * Produce a list of labels from these tags
     */
    protected Set<String> getTagLabels(List<AIMetadata.Tag> tags) {
        Set<String> labels = new HashSet<>();
        for (AIMetadata.Tag tag : tags) {
            String tagName = tag.name;
            labels.add(tagName);
            if (!tag.features.isEmpty()) {
                tag.features.forEach(feature -> labels.add(tagName + "/" + feature.getName()));
            }
        }
        return labels;
    }

}
