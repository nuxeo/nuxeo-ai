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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * Serves runtime AI models
 */
public interface ModelServingService {

    /**
     * Add a AI Model at runtime, making it available for serving.
     */
    void addModel(ModelDescriptor descriptor);

    /**
     * List current configured models
     */
    Collection<ModelDescriptor> listModels();

    /**
     * Get a RuntimeModel by Id
     */
    RuntimeModel getModel(String modelId);

    /**
     * Gets the document predicate for a Runtime Model by Id
     */
    Predicate<DocumentModel> getPredicate(String modelId);

    /**
     * Get all the input properties for all models that match the provided document.
     */
    Set<String> getInputs(DocumentModel document);

    /**
     * For each running model, evaluate if the supplied document passes the predicate test for a model, if so call the
     * model and return the results.
     */
    List<EnrichmentMetadata> predict(DocumentModel documentModel);
}
