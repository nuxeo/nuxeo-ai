/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.bulk;

import static org.nuxeo.ai.AIConstants.AUTO_CORRECTED;
import static org.nuxeo.ai.AIConstants.AUTO_FILLED;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;
import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.STATUS_STREAM;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_1;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ai.metadata.SuggestionMetadataWrapper;
import org.nuxeo.ai.services.DocMetadataService;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.StreamProcessorTopology;

/**
 * A bulk action to remove enrichment metadata
 */
public class BulkRemoveEnrichmentAction implements StreamProcessorTopology {

    public static final String ACTION_NAME = "bulkEnrichRemove";

    public static final String PARAM_MODEL = "modelId";

    public static final String PARAM_XPATHS = "xpaths";

    @Override
    public Topology getTopology(Map<String, String> options) {

        return Topology.builder()
                       .addComputation(RemovalComputation::new,
                                       Arrays.asList(INPUT_1 + ":" + ACTION_NAME, //
                                                     OUTPUT_1 + ":" + STATUS_STREAM))
                       .build();
    }

    public static class RemovalComputation extends AbstractBulkComputation {

        private static final Logger log = LogManager.getLogger(RemovalComputation.class);

        protected String modelId;

        protected Set<String> xPaths;

        public RemovalComputation() {
            super(ACTION_NAME);
        }

        @Override
        public void startBucket(String bucketKey) {
            BulkCommand command = getCurrentCommand();
            Serializable modelParam = command.getParam(PARAM_MODEL);
            Serializable xParam = command.getParam(PARAM_XPATHS);
            modelId = modelParam != null ? String.valueOf(modelParam) : null;
            xPaths = xParam != null ? getSet(xParam) : Collections.emptySet();
        }

        @Override
        protected void compute(CoreSession session, List<String> ids, Map<String, Serializable> properties) {
            DocMetadataService metadataService = Framework.getService(DocMetadataService.class);
            for (DocumentModel doc : loadDocuments(session, ids)) {
                if (doc.hasFacet(ENRICHMENT_FACET)) {
                    SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(doc);
                    Set<String> removalProperties;
                    if (modelId != null) {
                        List<LabelSuggestion> suggestions = wrapper.getSuggestionsByModel(modelId);
                        removalProperties = suggestions.stream()
                                                       .map(LabelSuggestion::getProperty)
                                                       .collect(Collectors.toSet());
                    } else if (!xPaths.isEmpty()) {
                        removalProperties = xPaths;
                    } else {
                        removalProperties = wrapper.getAutoProperties();
                    }
                    try {
                        for (String xPath : removalProperties) {
                            metadataService.resetAuto(doc, AUTO_CORRECTED, xPath, true);
                            metadataService.resetAuto(doc, AUTO_FILLED, xPath, true);
                            metadataService.removeSuggestionsForTargetProperty(doc, xPath);
                        }
                        session.saveDocument(doc);
                    } catch (PropertyException e) {
                        log.warn("Cannot update enrichment document: {}", doc.getId(), e);
                    }
                }
            }
        }

        /**
         * Almost the same as getList in CSVProjectionComputation
         */
        protected Set<String> getSet(Serializable value) {
            if (value == null) {
                return Collections.emptySet();
            }
            if (value instanceof List<?>) {
                List<?> objects = (List<?>) value;
                Set<String> values = new HashSet<>(objects.size());
                for (Object object : objects) {
                    if (object != null) {
                        values.add(object.toString());
                    }
                }
                return values;
            } else {
                log.debug("Illegal parameter '{}'", value);
                return Collections.emptySet();
            }
        }
    }
}
