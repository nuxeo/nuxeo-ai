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
import static org.nuxeo.ai.enrichment.EnrichmentProvider.UNSET;
import static org.nuxeo.ai.services.DocMetadataServiceImpl.hadBeenModified;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.configuration.ThresholdService;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.SuggestionMetadataWrapper;
import org.nuxeo.ai.services.DocMetadataService;
import org.nuxeo.ecm.automation.core.util.DocumentHelper;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.runtime.api.Framework;

/**
 * Autofill and AutoCorrect services.
 */
public class AutoServiceImpl implements AutoService {

    private static final Logger log = LogManager.getLogger(AutoServiceImpl.class);

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
        Property property;
        boolean alreadyAutofilled = docMetadata.isAutoFilled(xpath);
        try {
            property = doc.getProperty(xpath);
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

            if (setProperty(doc.getCoreSession(), doc, xpath, null, max.getName())) {
                String comment = String.format("Auto filled %s. (Confidence %s , Threshold %s)",
                                               xpath, max.getConfidence(), threshold);
                log.debug(comment);
                Framework.getService(DocMetadataService.class)
                         .updateAuto(docMetadata.getDoc(), AUTO_FILLED, xpath, null, comment);
            }
        } else {
            if (alreadyAutofilled) {
                // We autofilled but now the value didn't autofill so lets reset it
                Framework.getService(DocMetadataService.class)
                         .resetAuto(docMetadata.getDoc(), AUTO_FILLED, xpath, true);
            }
        }
    }

    protected void calculateAutoCorrect(SuggestionMetadataWrapper docMetadata, String xpath) {
        if (xpath.startsWith(UNSET) || docMetadata.isAutoFilled(xpath)) {
            // Nothing to do
            return;
        }
        float threshold = Framework.getService(ThresholdService.class)
                                   .getAutoCorrectThreshold(docMetadata.getDoc(), xpath);
        if (threshold > 1 || threshold <= 0) {
            // Impossible threshold
            return;
        }

        DocMetadataService metadataService = Framework.getService(DocMetadataService.class);
        DocumentModel doc = docMetadata.getDoc();
        Property property;
        boolean alreadyAutoCorrected = docMetadata.isAutoCorrected(xpath);
        try {
            property = doc.getProperty(xpath);
        } catch (PropertyNotFoundException e) {
            log.warn("Unknown auto correct property {} ", xpath);
            return;
        }

        AIMetadata.Label max = calculateMaxLabel(docMetadata.getSuggestionsByProperty(xpath), threshold);
        if (max != null) {
            Serializable oldValue = property.getValue();
            if (setProperty(doc.getCoreSession(), doc, xpath, oldValue, max.getName())) {
                String comment = String.format("Auto corrected %s. (Confidence %s , Threshold %s)",
                                               xpath, max.getConfidence(), threshold);
                log.debug(comment);
                if (alreadyAutoCorrected) {
                    // We already auto corrected so we don't need to save the value in the history
                    oldValue = null;
                }
                metadataService.updateAuto(docMetadata.getDoc(), AUTO_CORRECTED, xpath, oldValue, comment);
            }
        } else {
            if (alreadyAutoCorrected) {
                // We auto corrected but now the value didn't auto correct so lets reset it
                metadataService.resetAuto(docMetadata.getDoc(), AUTO_CORRECTED, xpath, true);
            }
        }
    }

    protected boolean setProperty(CoreSession session, DocumentModel doc, String key, Serializable currentValue, String newValue) {
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
        metadataService.resetAuto(doc, AUTO_CORRECTED, xPath, false);
        metadataService.resetAuto(doc, AUTO_FILLED, xPath, false);
        metadataService.removeSuggestionsForTargetProperty(doc, xPath);
    }

}
