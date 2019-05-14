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
import static org.nuxeo.ai.pipes.functions.PropertyUtils.notNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.enrichment.EnrichmentService;
import org.nuxeo.ai.metadata.SuggestionMetadata;
import org.nuxeo.ai.model.ModelProperty;
import org.nuxeo.ai.pipes.functions.Predicates;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolverService;
import org.nuxeo.ecm.directory.DirectoryEntryResolver;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * An implementation of a service that serves runtime AI models
 */
public class ModelServingServiceImpl extends DefaultComponent implements ModelServingService {

    public static final String AI_DATATYPES = "aidatatypes";

    private static final String MODELS_AP = "models";

    private static final Logger log = LogManager.getLogger(ModelServingServiceImpl.class);

    protected final Map<String, ModelDescriptor> configs = new HashMap<>();

    protected final Map<String, RuntimeModel> models = new HashMap<>();

    protected final Map<String, Predicate<DocumentModel>> predicates = new HashMap<>();

    protected final Map<String, Predicate<DocumentModel>> filterPredicates = new HashMap<>();

    protected DirectoryEntryResolver inputTypesResolver;

    /**
     * Makes a DocumentModel predicate including the properties
     */
    public static Predicate<DocumentModel> makePredicate(Set<ModelProperty> inputs, ModelDescriptor.ModelPredicate filter) {
        return makeFilterPredicate(filter).and(d -> inputs.stream().allMatch(i -> notNull(d, i.getName())));
    }

    /*
     * Makes a DocumentModel predicate based on just the filter
     */
    public static Predicate<DocumentModel> makeFilterPredicate(ModelDescriptor.ModelPredicate filter) {
        Predicate<DocumentModel> docPredicate = Predicates.doc();
        if (StringUtils.isNotBlank(filter.primaryType)) {
            docPredicate = docPredicate.and(d -> filter.primaryType.equals(d.getType()));
        }
        return docPredicate;
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
    }

    @Override
    public void addModel(ModelDescriptor descriptor) {

        if (!descriptor.getInputs().stream().allMatch(i -> getInputTypesResolver().validate(i.getType()))) {
            throw new IllegalArgumentException(String.format("The input types %s for service %s must be defined in the %s vocabulary",
                                                             descriptor.getInputs(), descriptor.id, AI_DATATYPES));
        }

        log.debug("Registering a custom model as {}, info is {}.", descriptor.id, descriptor.info);
        RuntimeModel model = descriptor.getModel();
        if (model instanceof EnrichmentService) {
            Framework.getService(AIComponent.class).addEnrichmentService(descriptor.id, (EnrichmentService) model);
        }
        models.put(descriptor.id, model);
        predicates.put(descriptor.id, makePredicate(descriptor.getInputs(), descriptor.filter));
        filterPredicates.put(descriptor.id, makeFilterPredicate(descriptor.filter));
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
    public Predicate<DocumentModel> getPredicate(String modelId) {
        return predicates.get(modelId);
    }

    @Override
    public Collection<RuntimeModel> getDocumentModels(DocumentModel document) {
        return filterPredicates.entrySet()
                               .stream()
                               .filter(e -> e.getValue().test(document))
                               .map(e -> models.get(e.getKey()))
                               .filter(Objects::nonNull)
                               .collect(Collectors.toList());
    }

    @Override
    public List<SuggestionMetadata> predict(DocumentModel document) {
        return predicates.entrySet()
                         .stream()
                         .filter(e -> e.getValue().test(document))
                         .map(e -> models.get(e.getKey()).predict(document))
                         .filter(Objects::nonNull)
                         .collect(Collectors.toList());
    }

    /**
     * Returns the DirectoryEntryResolver for the "InputOutputTypes" directory.
     */
    protected DirectoryEntryResolver getInputTypesResolver() {
        if (inputTypesResolver == null) {
            inputTypesResolver =
                    (DirectoryEntryResolver) Framework.getService(ObjectResolverService.class)
                                                      .getResolver(DirectoryEntryResolver.NAME,
                                                                   singletonMap(DirectoryEntryResolver.PARAM_DIRECTORY,
                                                                                AI_DATATYPES));
        }
        return inputTypesResolver;
    }
}
