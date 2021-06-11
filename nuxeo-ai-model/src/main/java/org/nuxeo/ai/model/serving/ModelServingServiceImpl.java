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
package org.nuxeo.ai.model.serving;

import static java.util.Collections.singletonMap;
import static org.nuxeo.ai.listeners.ContinuousExportListener.ENTRIES_KEY;
import static org.nuxeo.ai.pipes.functions.PropertyUtils.notNull;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentProvider;
import org.nuxeo.ai.listeners.InvalidateModelDefinitionsListener;
import org.nuxeo.ai.model.ModelProperty;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ai.services.AIConfigurationServiceImpl;
import org.nuxeo.ai.services.PersistedConfigurationService;
import org.nuxeo.ecm.core.api.CloseableCoreSession;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.ecm.core.event.impl.EventImpl;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolverService;
import org.nuxeo.ecm.directory.DirectoryEntryResolver;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.Component;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Descriptor;
import org.nuxeo.runtime.pubsub.PubSubService;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * An implementation of a service that serves runtime AI models
 */
public class ModelServingServiceImpl extends DefaultComponent implements ModelServingService {

    public static final String AI_DATATYPES = "aidatatypes";

    protected static final Serializable EMPTY_SET = (Serializable) Collections.emptyList();

    private static final TypeReference<Map<String, Serializable>> RESPONSE_TYPE_REFERENCE = new TypeReference<Map<String, Serializable>>() {
    };

    private static final String MODELS_AP = "models";

    private static final String MODEL_NAME_PROP = "ai_model:name";

    private static final String MODEL_DOC_TYPE_PROP = "ai_model:doc_type";

    private static final String MODEL_INPUTS_PROP = "ai_model:inputs";

    private static final String MODEL_OUTPUTS_PROP = "ai_model:outputs";

    private static final String MODEL_NAME_KEY = "modelName";

    private static final String PROPERTIES_KEY = "properties";

    private static final String NAME_KEY = "name";

    private static final String TYPE_KEY = "type";

    private static final Logger log = LogManager.getLogger(ModelServingServiceImpl.class);

    protected final Map<String, ModelDescriptor> configs = new HashMap<>();

    protected final Map<String, RuntimeModel> models = new HashMap<>();

    protected final Map<String, Predicate<DocumentModel>> predicates = new HashMap<>();

    protected final Map<String, Predicate<DocumentModel>> filterPredicates = new HashMap<>();

    protected DirectoryEntryResolver inputTypesResolver;

    /**
     * Makes a DocumentModel predicate including the properties
     */
    public static Predicate<DocumentModel> makePredicate(Set<ModelProperty> inputs,
            Predicate<DocumentModel> predicate) {
        return predicate.and(d -> inputs.stream().allMatch(i -> notNull(d, i.getName())));
    }

    @Override
    public void reload(Descriptor desc) {
        String labels = ((ModelDescriptor) desc).getInfo().get("modelLabel");
        this.registerContribution(desc, MODELS_AP, null);
        this.addModel((ModelDescriptor) desc);
    }

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (MODELS_AP.equals(extensionPoint)) {
            ModelDescriptor descriptor = (ModelDescriptor) contribution;
            this.configs.put(descriptor.id, descriptor);
        }
    }

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        configs.forEach((key, value) -> addModel(value));
        PubSubService pss = Framework.getService(PubSubService.class);
        if (pss != null) {
            pss.registerSubscriber(AIConfigurationServiceImpl.TOPIC, this::modelSubscriber);
            pss.registerSubscriber(INVALIDATOR_TOPIC, this::modelInvalidator);
        } else {
            log.warn("No Pub/Sub service available");
        }

        PersistedConfigurationService pcs = Framework.getService(PersistedConfigurationService.class);
        pcs.register(ModelDescriptor.class);

        fireInvalidationEvent();
    }

    @Override
    public int getApplicationStartedOrder() {
        Component component = (Component) Framework.getRuntime().getComponent("org.nuxeo.ai.cloud.NuxeoCloudClient");
        if (component == null) {
            // We make sure this component is loaded after the Nuxeo Cloud Client is being configured.
            return super.getApplicationStartedOrder() + 1;
        }
        return component.getApplicationStartedOrder() + 1;
    }

    protected void fireInvalidationEvent() {
        EventContextImpl ctx = new EventContextImpl();
        Event event = new EventImpl(InvalidateModelDefinitionsListener.EVENT_NAME, ctx);
        Framework.getService(EventService.class).fireEvent(event);
    }

    protected void modelSubscriber(String topic, byte[] message) {
        String contribKey = new String(message);
        PersistedConfigurationService pcs = Framework.getService(PersistedConfigurationService.class);
        try {
            Descriptor desc = pcs.retrieve(contribKey);
            if (desc == null) {
                this.models.remove(contribKey);
                this.predicates.remove(contribKey);
                this.filterPredicates.remove(contribKey);
            }
            if (desc instanceof ModelDescriptor) {
                this.reload(desc);
            }
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public void addModel(ModelDescriptor descriptor) {
        if (!descriptor.getInputs().stream().allMatch(i -> getInputTypesResolver().validate(i.getType()))) {
            throw new IllegalArgumentException(
                    String.format("The input types %s for service %s must be defined in the %s vocabulary",
                            descriptor.getInputs(), descriptor.id, AI_DATATYPES));
        }

        configs.put(descriptor.id, descriptor);

        log.debug("Registering a custom model as {}, info is {}.", descriptor.id, descriptor.info);
        RuntimeModel model = descriptor.getModel();
        if (model instanceof EnrichmentProvider) {
            Framework.getService(AIComponent.class).addEnrichmentProvider(descriptor.id, (EnrichmentProvider) model);
        }

        models.put(descriptor.id, model);
        predicates.put(descriptor.id, makePredicate(descriptor.getInputs(), descriptor.filter.get()));
        filterPredicates.put(descriptor.id, descriptor.filter.get());
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        this.inputTypesResolver = null;
        this.models.clear();
        this.predicates.clear();
    }

    @Override
    public Collection<ModelDescriptor> listModels() {
        return configs.values();
    }

    @Override
    public RuntimeModel getModel(String modelId) {
        return models.get(modelId);
    }

    @Override
    public RuntimeModel deleteModel(String modelId) {
        return models.remove(modelId);
    }

    @Override
    public Predicate<DocumentModel> getPredicate(String modelId) {
        return predicates.get(modelId);
    }

    @Override
    public Set<ModelProperty> getInputs(DocumentModel document) {
        return filterPredicates.entrySet()
                               .stream()
                               .filter(e -> e.getValue().test(document))
                               .map(e -> models.get(e.getKey()))
                               .filter(Objects::nonNull)
                               .flatMap(m -> m.getInputs().stream())
                               .collect(Collectors.toSet());
    }

    @Override
    public List<EnrichmentMetadata> predict(DocumentModel document) {
        return predicates.entrySet()
                         .stream()
                         .filter(e -> e.getValue().test(document))
                         .map(e -> models.get(e.getKey()).predict(document))
                         .filter(Objects::nonNull)
                         .collect(Collectors.toList());
    }

    protected void modelInvalidator(String topic, byte[] message) {
        log.info("Model Invalidation received");
        String defaultRepository = Framework.getService(RepositoryManager.class).getDefaultRepository().getName();
        try (CloseableCoreSession session = CoreInstance.openCoreSessionSystem(defaultRepository)) {
            CloudClient cc = Framework.getService(CloudClient.class);
            JSONBlob published = cc.getPublishedModels(session);

            Map<String, Serializable> resp = MAPPER.readValue(published.getStream(), RESPONSE_TYPE_REFERENCE);
            if (resp.containsKey(ENTRIES_KEY)) {
                clearAll();

                @SuppressWarnings("unchecked")
                List<Map<String, Serializable>> entries = (List<Map<String, Serializable>>) resp.get(ENTRIES_KEY);

                Map<String, ModelDescriptor> newModels = entries.stream()
                                                                .map(this::construct)
                                                                .collect(Collectors.toMap(
                                                                        desc -> desc.info.get(MODEL_NAME_KEY),
                                                                        desc -> desc));
                newModels.values().forEach(this::addModel);
                log.info("Insight cloud has {} published model definitions; Model registry size after update {}",
                        newModels.size(), models.size());
            } else {
                log.warn("No active models were found");
            }
        } catch (IOException e) {
            log.error(e);
            throw new NuxeoException(e);
        }
    }

    protected void clearAll() {
        configs.clear();
        models.clear();
        predicates.clear();
        filterPredicates.clear();
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

    /**
     * Returns the DirectoryEntryResolver for the "InputOutputTypes" directory.
     */
    protected DirectoryEntryResolver getInputTypesResolver() {
        if (inputTypesResolver == null) {
            inputTypesResolver = (DirectoryEntryResolver) Framework.getService(ObjectResolverService.class)
                                                                   .getResolver(DirectoryEntryResolver.NAME,
                                                                           singletonMap(
                                                                                   DirectoryEntryResolver.PARAM_DIRECTORY,
                                                                                   AI_DATATYPES));
        }
        return inputTypesResolver;
    }
}
