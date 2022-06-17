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
package org.nuxeo.ai.auto;

import static org.nuxeo.ai.auto.AutoService.AUTO_ACTION.ALL;
import static org.nuxeo.ai.enrichment.EnrichmentProvider.UNSET;
import static org.nuxeo.ai.services.DocMetadataServiceImpl.hadBeenModified;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AIConstants.AUTO;
import org.nuxeo.ai.configuration.ThresholdService;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.SuggestionMetadataWrapper;
import org.nuxeo.ai.services.DocMetadataService;
import org.nuxeo.ecm.automation.core.util.DocumentHelper;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.runtime.api.Framework;

/**
 * Autofill and AutoCorrect services.
 */
public class AutoServiceImpl implements AutoService {

    private static final Logger log = LogManager.getLogger(AutoServiceImpl.class);

    protected SchemaManager schemaManager = null;

    @Override
    public void calculateProperties(DocumentModel doc) {
        calculateProperties(doc, ALL);
    }

    @Override
    public void calculateProperties(DocumentModel doc, AUTO_ACTION action) {
        SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(doc);
        for (String xpath : wrapper.getAutoProperties()) {
            switch (action) {
            case ALL:
                calculateAutoFill(wrapper, xpath);
                calculateAutoCorrect(wrapper, xpath);
                break;
            case CORRECT:
                calculateAutoCorrect(wrapper, xpath);
                break;
            case FILL:
                calculateAutoFill(wrapper, xpath);
            }
        }
    }

    protected boolean canApplyProperty(DocumentModel input, String property) {
        String prefix = getSchemaManager().getField(property).getName().getPrefix();
        Schema schema = getSchemaManager().getSchemaFromPrefix(prefix);
        if (schema != null && input.hasSchema(schema.getName())) {
            return true;
        }

        if (log.isDebugEnabled() && schema != null) {
            log.debug("Document {} of type {} does not contain schema {}", input.getId(), input.getType(),
                    schema.getSchemaName());
        } else {
            log.error("No such schema from prefix {}", prefix);
        }

        return false;
    }

    protected SchemaManager getSchemaManager() {
        if (schemaManager == null) {
            schemaManager = Framework.getService(SchemaManager.class);
        }

        return schemaManager;
    }

    protected void calculateAutoFill(SuggestionMetadataWrapper docMetadata, String xpath) {
        if (xpath.startsWith(UNSET)) {
            // Nothing to do, no property specified
            return;
        }
        float threshold = Framework.getService(ThresholdService.class)
                                   .getAutoFillThreshold(docMetadata.getDoc(), xpath);
        if (threshold > 1 || threshold <= 0) {
            // Impossible threshold
            return;
        }
        DocumentModel doc = docMetadata.getDoc();
        if (!canApplyProperty(doc, xpath)) {
            return;
        }

        boolean alreadyAutofilled = docMetadata.isAutoFilled(xpath);
        if (!alreadyAutofilled && docMetadata.hasValue(xpath)) {
            log.debug("Unable to autofill property {} for doc {} because it has a value.", xpath,
                    docMetadata.getDoc().getId());
            return;
        }

        List<SuggestionMetadataWrapper.PropertyHolder> modelSuggestions = docMetadata.getSuggestionsByProperty(xpath);

        boolean autofilled = false;
        Property property = doc.getProperty(xpath);
        if (property.isList()) {
            SuggestionMetadataWrapper.PropertyHolder reduce = modelSuggestions.stream()
                                                                              .reduce(reduceLabels(threshold))
                                                                              .orElse(null);
            if (reduce != null) {
                List<String> values = reduce.getLabels()
                                            .stream()
                                            .filter(label -> label.getConfidence() >= threshold)
                                            .map(AIMetadata.Label::getName)
                                            .collect(Collectors.toList());
                doc.setPropertyValue(xpath, (Serializable) values);
                String comment = String.format("Auto filled a list %s. (Threshold %s)", xpath, threshold);
                log.debug(comment);

                Framework.getService(DocMetadataService.class)
                         .updateAuto(docMetadata.getDoc(), AUTO.FILLED, xpath, reduce.getModel(), null, comment);
                docMetadata.addAutoFilled(xpath, reduce.getModel());
                autofilled = true;
            }
        } else {
            SuggestionMetadataWrapper.PropertyHolder reduce = modelSuggestions.stream()
                                                                              .reduce(reduceLabels(threshold))
                                                                              .orElse(null);
            if (reduce != null) {
                AIMetadata.Label max = calculateMaxLabel(reduce.getLabels(), threshold);
                if (max != null) {
                    float maxConfidence = max.getConfidence();

                    if (setProperty(doc.getCoreSession(), doc, xpath, null, max.getName())) {
                        String comment = String.format("Auto filled %s. (Confidence %s , Threshold %s)", xpath,
                                maxConfidence, threshold);
                        log.debug(comment);

                        Framework.getService(DocMetadataService.class)
                                 .updateAuto(docMetadata.getDoc(), AUTO.FILLED, xpath, reduce.getModel(), null,
                                         comment);
                        docMetadata.addAutoFilled(xpath, reduce.getModel());
                        autofilled = true;
                    }
                }
            }
        }

        if (!autofilled && alreadyAutofilled) {
            // We autofilled but now the value didn't autofill so lets reset it
            Framework.getService(DocMetadataService.class).resetAuto(docMetadata.getDoc(), AUTO.FILLED, xpath, true);
        }
    }

    private BinaryOperator<SuggestionMetadataWrapper.PropertyHolder> reduceLabels(float threshold) {
        return (o1, o2) -> {
            AIMetadata.Label maxO1 = calculateMaxLabel(o1.getLabels(), threshold);
            AIMetadata.Label maxO2 = calculateMaxLabel(o2.getLabels(), threshold);
            if (maxO1 == null || maxO2 == null) {
                return maxO1 == null ? o2 : o1;
            }

            return maxO1.getConfidence() > maxO2.getConfidence() ? o1 : o2;
        };
    }

    protected void calculateAutoCorrect(SuggestionMetadataWrapper metadata, String xpath) {
        if (xpath.startsWith(UNSET) || metadata.isAutoFilled(xpath)) {
            // Nothing to do
            return;
        }
        float threshold = Framework.getService(ThresholdService.class)
                                   .getAutoCorrectThreshold(metadata.getDoc(), xpath);
        if (threshold > 1 || threshold <= 0) {
            // Impossible threshold
            return;
        }

        DocMetadataService metadataService = Framework.getService(DocMetadataService.class);
        DocumentModel doc = metadata.getDoc();
        if (!canApplyProperty(doc, xpath)) {
            return;
        }

        Property property;
        boolean alreadyAutoCorrected = metadata.isAutoCorrected(xpath);
        try {
            property = doc.getProperty(xpath);
        } catch (PropertyNotFoundException e) {
            log.warn("Unknown auto correct property {} ", xpath);
            return;
        }

        SuggestionMetadataWrapper.PropertyHolder reduce = metadata.getSuggestionsByProperty(xpath)
                                                                  .stream()
                                                                  .reduce(reduceLabels(threshold))
                                                                  .orElse(null);
        AIMetadata.Label max = null;
        if (reduce != null) {
            max = calculateMaxLabel(reduce.getLabels(), threshold);
        }

        if (max != null) {
            Serializable oldValue = property.getValue();
            if (setProperty(doc.getCoreSession(), doc, xpath, oldValue, max.getName())) {
                String comment = String.format("Auto corrected %s. (Confidence %s , Threshold %s)", xpath,
                        max.getConfidence(), threshold);
                log.debug(comment);
                if (alreadyAutoCorrected) {
                    // We already auto corrected so we don't need to save the value in the history
                    oldValue = null;
                }

                metadataService.updateAuto(metadata.getDoc(), AUTO.CORRECTED, xpath, reduce.getModel(), oldValue,
                        comment);
            }
        } else {
            if (alreadyAutoCorrected) {
                // We auto corrected but now the value didn't auto correct so lets reset it
                metadataService.resetAuto(metadata.getDoc(), AUTO.CORRECTED, xpath, true);
            }
        }
    }

    protected boolean setProperty(CoreSession session, DocumentModel doc, String key, Serializable currentValue,
            String newValue) {
        if (!newValue.equals(currentValue)) {
            try {
                DocumentHelper.setProperty(session, doc, key, newValue);
                return true;
            } catch (IOException e) {
                log.debug("Failed to set property " + key, e);
            }
        }
        return false;
    }

    protected AIMetadata.Label calculateMaxLabel(List<AIMetadata.Label> labels, float threshold) {
        if (labels != null) {
            Optional<AIMetadata.Label> label = labels.stream()
                                                     .max(Comparator.comparing(AIMetadata.Label::getConfidence));
            if (label.isPresent()) {
                if (label.get().getConfidence() >= threshold) {
                    return label.get();
                }
            }
        }
        return null;
    }

    @Override
    public void autoApproveDirtyProperties(DocumentModel doc) {
        SuggestionMetadataWrapper wrapper = new SuggestionMetadataWrapper(doc);
        for (String xPath : wrapper.getAutoProperties()) {
            if (hadBeenModified(doc, Collections.singleton(xPath))) {
                approveAutoProperty(doc, xPath);
            }
        }
    }

    @Override
    public void approveAutoProperty(DocumentModel doc, String xPath) {
        DocMetadataService metadataService = Framework.getService(DocMetadataService.class);
        metadataService.resetAuto(doc, AUTO.CORRECTED, xPath, false);
        metadataService.resetAuto(doc, AUTO.FILLED, xPath, false);
        metadataService.removeSuggestionsForTargetProperty(doc, xPath);
    }

}
