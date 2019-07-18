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

import static org.nuxeo.ai.AIConstants.AUTO_CORRECTED;
import static org.nuxeo.ai.AIConstants.AUTO_FILLED;
import static org.nuxeo.ai.auto.AutoService.AUTO_ACTION.ALL;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.configuration.ThresholdService;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.SuggestionMetadataAdapter;
import org.nuxeo.ai.services.DocMetadataService;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.runtime.api.Framework;

/**
 * Autofill and AutoCorrect services
 */
public class AutoServiceImpl implements AutoService {

    private static final Logger log = LogManager.getLogger(AutoServiceImpl.class);

    @Override
    public void calculateProperties(DocumentModel doc) {
        calculateProperties(doc, ALL);
    }

    @Override
    public void calculateProperties(DocumentModel doc, AUTO_ACTION action) {
        SuggestionMetadataAdapter adapted = doc.getAdapter(SuggestionMetadataAdapter.class);
        for (String xpath : adapted.getSuggestedProperties()) {
            switch (action) {
                case ALL:
                    calculateAutoCorrect(adapted, xpath);
                    calculateAutoFill(adapted, xpath);
                    break;
                case CORRECT:
                    calculateAutoCorrect(adapted, xpath);
                    break;
                case FILL:
                    calculateAutoFill(adapted, xpath);
            }
        }
    }

    protected void calculateAutoFill(SuggestionMetadataAdapter docMetadata, String xpath) {
        float threshold = Framework.getService(ThresholdService.class)
                                   .getAutoFillThreshold(docMetadata.getDoc(), xpath);
        Property property;
        boolean alreadyAutofilled = docMetadata.isAutoFilled(xpath);
        try {
            property = docMetadata.getDoc().getProperty(xpath);
            if (!alreadyAutofilled && property.getValue() != null) {
                // Can't set a non null value
                log.debug("Unable to autofill property {} for doc {} because it has a value.", xpath,
                          docMetadata.getDoc().getId());
                return;
            }
        } catch (PropertyNotFoundException e) {
            log.warn("Unknown autofill property {} ", xpath);
            return;
        }

        AIMetadata.Label max = calculateMaxLabel(docMetadata.getSuggestionsByProperty(xpath), threshold);
        if (max != null) {
            String comment = String.format("Auto filled %s. (Confidence %s , Threshold %s)",
                                           xpath, max.getConfidence(), threshold);
            log.debug(comment);
            property.setValue(max.getName());
            Framework.getService(DocMetadataService.class)
                     .updateAuto(docMetadata.getDoc(), AUTO_FILLED, xpath, null, comment);
        } else {
            if (alreadyAutofilled) {
                // We autofilled but now the value didn't autofill so lets reset it
                Framework.getService(DocMetadataService.class)
                         .resetAuto(docMetadata.getDoc(), AUTO_FILLED, xpath, true);
            }
        }
    }

    protected void calculateAutoCorrect(SuggestionMetadataAdapter docMetadata, String xpath) {
        float threshold = Framework.getService(ThresholdService.class)
                                   .getAutoCorrectThreshold(docMetadata.getDoc(), xpath);

        DocMetadataService metadataService = Framework.getService(DocMetadataService.class);
        Property property;
        boolean alreadyAutoCorrected = docMetadata.isAutoCorrected(xpath);
        try {
            property = docMetadata.getDoc().getProperty(xpath);
        } catch (PropertyNotFoundException e) {
            log.warn("Unknown auto correct property {} ", xpath);
            return;
        }

        AIMetadata.Label max = calculateMaxLabel(docMetadata.getSuggestionsByProperty(xpath), threshold);
        if (max != null) {
            String comment = String.format("Auto corrected %s. (Confidence %s , Threshold %s)",
                                           xpath, max.getConfidence(), threshold);
            log.debug(comment);
            Serializable oldValue = property.getValue();
            property.setValue(max.getName());
            if (alreadyAutoCorrected) {
                // We already auto corrected so we don't need to save the value in the history
                oldValue = null;
            }
            metadataService.updateAuto(docMetadata.getDoc(), AUTO_CORRECTED, xpath, oldValue, comment);
            if (docMetadata.isAutoFilled(xpath)) {
                // Autofill and Auto correct are mutually exclusive, we have auto corrected so lets remove autofill.
                metadataService.resetAuto(docMetadata.getDoc(), AUTO_FILLED, xpath, false);
            }
        } else {
            if (alreadyAutoCorrected) {
                // We auto corrected but now the value didn't auto correct so lets reset it
                metadataService.resetAuto(docMetadata.getDoc(), AUTO_CORRECTED, xpath, true);
            }
        }
    }

    protected AIMetadata.Label calculateMaxLabel(List<AIMetadata.Label> labels, float threshold) {
        if (labels != null) {
            Optional<AIMetadata.Label> label = labels.stream()
                                                     .max(Comparator.comparing(AIMetadata.Label::getConfidence));
            if (label.isPresent()) {
                if (label.get().getConfidence() > threshold) {
                    return label.get();
                }
            }
        }
        return null;
    }

    @Override
    public void approveAutoProperty(DocumentModel doc, String xPath) {
        DocMetadataService metadataService = Framework.getService(DocMetadataService.class);
        metadataService.removeSuggestionsForTargetProperty(doc, xPath);
        metadataService.resetAuto(doc, AUTO_CORRECTED, xPath, false);
        metadataService.resetAuto(doc, AUTO_FILLED, xPath, false);
    }

}
