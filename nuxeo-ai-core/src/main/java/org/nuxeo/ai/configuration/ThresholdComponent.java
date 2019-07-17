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
 *     Andrei Nechaev
 */
package org.nuxeo.ai.configuration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.configuration.ThresholdConfiguratorDescriptor.Threshold;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Descriptor;

/**
 * Implementation of the ThresholdService
 */
public class ThresholdComponent extends DefaultComponent implements ThresholdService {

    private static final Logger log = LogManager.getLogger(ThresholdComponent.class);

    private static final float UNREACHABLE_CONFIDENCE_LEVEL = 2.f;

    public static final String THRESHOLD_CONFIGURATION_XP = "thresholdConfiguration";

    public static final String DEFAULT_THRESHOLD_VALUE = "nuxeo.ai.default.threshold";

    public static final String AUTOFILL_DEFAULT_VALUE = "nuxeo.ai.autofill.default.threshold";

    public static final String AUTO_CORRECT_DEFAULT_VALUE = "nuxeo.ai.autocorrect.default.threshold";

    protected float globalThreshold = UNREACHABLE_CONFIDENCE_LEVEL;

    protected float globalAutofillThreshold = UNREACHABLE_CONFIDENCE_LEVEL;

    protected float globalAutocorrectThreshold = UNREACHABLE_CONFIDENCE_LEVEL;

    protected final Map<String, Float> typeDefaultThresholds = new HashMap<>();

    protected final Map<String, Map<String, Threshold>> typeThresholds = new HashMap<>();

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        setDefaultThresholds();

        List<Descriptor> descriptors = getDescriptors(THRESHOLD_CONFIGURATION_XP);
        for (Descriptor d : descriptors) {
            ThresholdConfiguratorDescriptor descriptor = (ThresholdConfiguratorDescriptor) d;

            String type = descriptor.getType();

            float typeGlobal = descriptor.getGlobal();
            if (typeGlobal > 0.f && typeGlobal <= 1.f) {
                typeDefaultThresholds.put(type, typeGlobal);
            }

            typeThresholds.computeIfAbsent(type, key -> new HashMap<>());

            for (Threshold t : descriptor.getThresholds()) {
                Map<String, Threshold> map = typeThresholds.get(type);
                t.merge(map.get(t.getXPath()));
                map.put(t.getXPath(), t);
            }
        }
    }


    @Override
    public float getThreshold(DocumentModel doc, String xpath) {
        float result = -1.f;
        final String docType = doc.getType();

        Set<String> keys = typeThresholds.keySet();
        if (keys.contains(docType)) {
            Map<String, Threshold> typeTshlds = typeThresholds.get(docType);
            if (typeTshlds.containsKey(xpath)) {
                result = typeTshlds.get(xpath).getValue();
            }
        }

        Set<String> facets = doc.getFacets();
        boolean hasFacet = !Collections.disjoint(keys, facets);
        if (hasFacet) {
            for (String facet : facets) {
                if (keys.contains(facet) && typeThresholds.get(facet).containsKey(xpath)) {
                    result = Math.max(result, typeThresholds.get(facet).get(xpath).getValue());
                }
            }
        }

        if (result < 0.f &&  (typeDefaultThresholds.containsKey(docType) || hasFacet)) {
            result = getThresholdFor(docType, facets);
        }

        return result > 0.f ? result : globalThreshold;
    }

    @Override
    public float getAutoFillThreshold(DocumentModel doc, String xpath) {
        float result = -1.f;
        final String docType = doc.getType();

        Set<String> keys = typeThresholds.keySet();
        if (keys.contains(docType)) {
            Map<String, Threshold> typeTshlds = typeThresholds.get(docType);
            if (typeTshlds.containsKey(xpath)) {
                result = typeTshlds.get(xpath).getAutofillValue();
            }
        }

        Set<String> facets = doc.getFacets();
        boolean hasFacet = !Collections.disjoint(keys, facets);
        if (hasFacet) {
            for (String facet : facets) {
                if (keys.contains(facet) && typeThresholds.get(facet).containsKey(xpath)) {
                    result = Math.max(result, typeThresholds.get(facet).get(xpath).getAutofillValue());
                }
            }
        }

        if (result < 0.f &&  (typeDefaultThresholds.containsKey(docType) || hasFacet)) {
            result = getThresholdFor(docType, facets);
        }

        return result > 0.f ? result : globalAutofillThreshold;
    }


    @Override
    public float getAutoCorrectThreshold(DocumentModel doc, String xpath) {
        String docType = doc.getType();
        float result = -1.f;

        Set<String> keys = typeThresholds.keySet();
        if (keys.contains(docType)) {
            Map<String, Threshold> typeTshlds = typeThresholds.get(docType);
            if (typeTshlds.containsKey(xpath)) {
                result = typeTshlds.get(xpath).getAutocorrect();
            }
        }

        Set<String> facets = doc.getFacets();
        boolean hasFacet = !Collections.disjoint(keys, facets);
        if (hasFacet) {
            for (String facet : facets) {
                if (keys.contains(facet) && typeThresholds.get(facet).containsKey(xpath)) {
                    result = Math.max(result, typeThresholds.get(facet).get(xpath).getAutocorrect());
                }
            }
        }

        if (result < 0.f &&  (typeDefaultThresholds.containsKey(docType) || hasFacet)) {
            result = getThresholdFor(docType, facets);
        }

        return result > 0.f ? result : globalAutocorrectThreshold;
    }

    protected void setDefaultThresholds() {
        globalThreshold = getDefaultThresholdFor(DEFAULT_THRESHOLD_VALUE).orElse(UNREACHABLE_CONFIDENCE_LEVEL);
        globalAutofillThreshold = getDefaultThresholdFor(AUTOFILL_DEFAULT_VALUE).orElse(UNREACHABLE_CONFIDENCE_LEVEL);
        globalAutocorrectThreshold = getDefaultThresholdFor(AUTO_CORRECT_DEFAULT_VALUE).orElse(UNREACHABLE_CONFIDENCE_LEVEL);
    }

    public Optional<Float> getDefaultThresholdFor(String key) {
        return Optional.ofNullable(Framework.getProperty(key)).map((value) -> {
            try {
                return Float.valueOf(value);
            } catch (NumberFormatException e) {
                log.error("Invalid configuration property '{}', '{}' should be a number", key, value, e);
                return null;
            }
        });
    }

    protected float getThresholdFor(String docType, Set<String> facets) {
        float result;
        result = typeDefaultThresholds.getOrDefault(docType, -1.f);
        for (String f : facets) {
            result = Math.max(result, typeDefaultThresholds.getOrDefault(f, -1.f));
        }
        return result;
    }
}
