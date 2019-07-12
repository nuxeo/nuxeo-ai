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
package org.nuxeo.ai.metadata;

import static org.nuxeo.ai.AIConstants.ENRICHMENT_FACET;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.adapter.DocumentAdapterFactory;

/**
 * An adapter factory for enrichment metadata.
 */
public class EnrichmentAdapterFactory implements DocumentAdapterFactory {

    @Override
    public Object getAdapter(DocumentModel documentModel, Class<?> aClass) {
        if (documentModel.hasFacet(ENRICHMENT_FACET)) {
            return new SuggestionMetadataAdapter(documentModel);
        }
        return null;
    }
}
