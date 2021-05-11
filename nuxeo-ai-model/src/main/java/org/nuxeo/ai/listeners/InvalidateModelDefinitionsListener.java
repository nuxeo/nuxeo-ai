/*
 *   (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *
 *   Contributors:
 *       anechaev
 */
package org.nuxeo.ai.listeners;

import static org.nuxeo.ai.listeners.ContinuousExportListener.ENTRIES_KEY;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.model.ModelProperty;
import org.nuxeo.ai.model.serving.ModelDescriptor;
import org.nuxeo.ai.model.serving.ModelServingService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.runtime.api.Framework;

/**
 * Invalidate and Update currently running models
 */
public class InvalidateModelDefinitionsListener implements PostCommitEventListener {

    private static final Logger log = LogManager.getLogger(InvalidateModelDefinitionsListener.class);

    private static final String MODEL_NAME_PROP = "ai_model:name";

    private static final String MODEL_DOC_TYPE_PROP = "ai_model:doc_type";

    private static final String MODEL_INPUTS_PROP = "ai_model:inputs";

    private static final String MODEL_OUTPUTS_PROP = "ai_model:outputs";

    private static final String MODEL_NAME_KEY = "modelName";

    private static final String PROPERTIES_KEY = "properties";

    private static final String NAME_KEY = "name";

    private static final String TYPE_KEY = "type";

    protected static final Serializable EMPTY_SET = (Serializable) Collections.emptyList();

    public static final String EVENT_NAME = "invalidateModelDefinitions";

    @Override
    public void handleEvent(EventBundle bundle) {
        ModelServingService mss = Framework.getService(ModelServingService.class);
        Collection<ModelDescriptor> descriptors = mss.listModels();
        Map<String, ModelDescriptor> oldModels = descriptors.stream()
                                                            .filter(desc -> desc.info.containsKey(MODEL_NAME_KEY))
                                                            .collect(Collectors.toMap(
                                                                    desc -> desc.info.get(MODEL_NAME_KEY), desc -> desc,
                                                                    (o, o2) -> o2));

        try {
            CloudClient cc = Framework.getService(CloudClient.class);
            JSONBlob models = cc.getPublishedModels();
            @SuppressWarnings("unchecked")
            Map<String, Serializable> resp = MAPPER.readValue(models.getStream(), Map.class);
            if (resp.containsKey(ENTRIES_KEY)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Serializable>> entries = (List<Map<String, Serializable>>) resp.get(ENTRIES_KEY);

                Map<String, ModelDescriptor> news = entries.stream()
                                                           .map(this::construct)
                                                           .collect(Collectors.toMap(
                                                                   desc -> desc.info.get(MODEL_NAME_KEY),
                                                                   desc -> desc));
                Map<String, ModelDescriptor> all = news.values()
                          .stream()
                          .collect(Collectors.toMap(desc -> desc.info.get(MODEL_NAME_KEY), desc -> desc,
                                  (o1, o2) -> merge(oldModels, news, o1.info.get(MODEL_NAME_KEY))));

                log.info("Insight cloud has {} model definitions; Model registry size after update {}", news.size(),
                        all.size());
                all.values().forEach(mss::addModel);
            } else {
                log.warn("No active models were found");
            }
        } catch (IOException e) {
            log.error(e);
            throw new NuxeoException(e);
        }
    }

    protected ModelDescriptor merge(Map<String, ModelDescriptor> oldModels, Map<String, ModelDescriptor> news,
            String modelName) {
        ModelDescriptor newModel = news.get(modelName);
        ModelDescriptor oldModel = oldModels.get(modelName);
        newModel.id = oldModel.id;
        return newModel;
    }

    protected ModelDescriptor construct(Map<String, Serializable> entry) {
        @SuppressWarnings("unchecked")
        Map<String, Serializable> properties = (Map<String, Serializable>) entry.get(PROPERTIES_KEY);
        String name = (String) properties.get(MODEL_NAME_PROP);
        String type = (String) properties.get(MODEL_DOC_TYPE_PROP);

        ModelDescriptor.InputProperties inputProperties = new ModelDescriptor.InputProperties();
        inputProperties.setProperties(toSet(properties, MODEL_INPUTS_PROP));

        ModelDescriptor.OutputProperties outputProperties = new ModelDescriptor.OutputProperties();
        outputProperties.setProperties(toSet(properties, MODEL_OUTPUTS_PROP));

        ModelDescriptor des = new ModelDescriptor();
        des.id = name;
        des.inputProperties = inputProperties;
        des.outputProperties = outputProperties;

        HashMap<String, String> info = new HashMap<>();
        info.put(MODEL_NAME_KEY, name);

        ModelDescriptor.DocumentPredicate predicate = new ModelDescriptor.DocumentPredicate();
        predicate.primaryType = type;

        des.info = info;
        des.filter = predicate;

        return des;
    }

    private Set<ModelProperty> toSet(Map<String, Serializable> entry, String name) {
        @SuppressWarnings("unchecked")
        List<Map<String, Serializable>> outputs = (List<Map<String, Serializable>>) entry.getOrDefault(name, EMPTY_SET);
        return getModelProperties(outputs);
    }

    private Set<ModelProperty> getModelProperties(List<Map<String, Serializable>> inputs) {
        return inputs.stream()
                     .map(input -> new ModelProperty((String) input.get(NAME_KEY), (String) input.get(TYPE_KEY)))
                     .collect(Collectors.toSet());
    }

}
