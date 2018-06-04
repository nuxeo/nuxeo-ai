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
import static org.nuxeo.ai.AIConstants.ENRICHMENT_CLASSIFICATIONS;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_KIND_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_LABELS_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_NAME;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_RAW_KEY_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_TARGET_DOCPROP_PROPERTY;
import static org.nuxeo.ai.AIConstants.NORMALIZED_PROPERTY;
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.MAPPER;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * An implementation of DocMetadataService
 */
public class DocMetadataServiceImpl extends DefaultComponent implements DocMetadataService {

    private static final Log log = LogFactory.getLog(DocMetadataServiceImpl.class);

    @Override
    public DocumentModel saveEnrichment(CoreSession session, EnrichmentMetadata metadata) {
        //TODO: Handle versions here? and doc not found
        DocumentModel doc = session.getDocument(new IdRef(metadata.getTargetDocumentRef()));
        if (!doc.hasFacet(ENRICHMENT_FACET)) {
            doc.addFacet(ENRICHMENT_FACET);
        }

        Map<String, Object> anItem = createClassification(metadata);

        if (anItem != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> classifications =
                    (List) doc.getProperty(ENRICHMENT_NAME, ENRICHMENT_CLASSIFICATIONS);
            if (classifications == null) {
                classifications = new ArrayList<>(1);
            }
            classifications.add(anItem);
            doc.setProperty(ENRICHMENT_NAME, ENRICHMENT_CLASSIFICATIONS, classifications);
            return session.saveDocument(doc);
        } else {
            return doc;
        }

    }

    /**
     * Create a classification Map using the enrichment metadata
     */
    protected Map<String, Object> createClassification(EnrichmentMetadata metadata) {
        Map<String, Object> anEntry = new HashMap<>();
        AIComponent aiComponent = Framework.getService(AIComponent.class);

        List<String> labels = metadata.getLabels().stream()
                                      .map(EnrichmentMetadata.Label::getName)
                                      .filter(Objects::nonNull)
                                      .distinct()
                                      .collect(Collectors.toList());

        if (labels != null && !labels.isEmpty()) {
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
            anEntry.put(ENRICHMENT_TARGET_DOCPROP_PROPERTY, metadata.getTargetDocumentProperties());
            anEntry.put(ENRICHMENT_RAW_KEY_PROPERTY, rawBlob);
            anEntry.put(NORMALIZED_PROPERTY, metaDataBlob);
            if (StringUtils.isNotBlank(metadata.getCreator())) {
                anEntry.put(AI_CREATOR_PROPERTY, metadata.getCreator());
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug(String.format("No labels for %s so not saving any enrichment data", metadata
                        .getTargetDocumentRef()));
            }
            return null;
        }

        return anEntry;
    }

}
