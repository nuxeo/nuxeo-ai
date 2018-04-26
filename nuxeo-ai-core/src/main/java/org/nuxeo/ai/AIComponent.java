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
package org.nuxeo.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ai.enrichment.EnrichmentDescriptor;
import org.nuxeo.ai.enrichment.EnrichmentService;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeEntry;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Provides one or more services using AI
 */
public class AIComponent extends DefaultComponent {

    public static final String ENRICHMENT = "enrichment";

    protected final Map<String, EnrichmentDescriptor> enrichmentConfigs = new HashMap<>();
    protected final Map<String, EnrichmentService> enrichmentServices = new HashMap<>();

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (ENRICHMENT.equals(extensionPoint)) {
            EnrichmentDescriptor descriptor = (EnrichmentDescriptor) contribution;
            enrichmentConfigs.put(descriptor.name, descriptor);
        }
    }

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        enrichmentConfigs.values().forEach(descriptor -> {
            if (!enrichmentServices.containsKey(descriptor.name)) {
                initialize(descriptor);
            }
        });
    }

    /**
     * Initialize an enrichment descriptor
     */
    public synchronized void initialize(EnrichmentDescriptor descriptor) {
        if (!enrichmentServices.containsKey(descriptor.name)) {
            MimetypeRegistry mimeRegistry = Framework.getService(MimetypeRegistry.class);
            EnrichmentService enrichmentService = descriptor.getService();
            List<String> mimeTypes = new ArrayList<>();
            descriptor.getMimeTypes().forEach(mimeType -> {
                if (mimeType.normalized) {
                    MimetypeEntry entry = mimeRegistry.getMimetypeEntryByMimeType(mimeType.name);
                    mimeTypes.addAll(entry.getMimetypes());
                } else {
                    mimeTypes.add(mimeType.name);
                }
            });
            enrichmentService.addMimeTypes(mimeTypes);
            enrichmentServices.put(descriptor.name, enrichmentService);
        }
    }

    /**
     * Returns the names of all the enrichment services that are configured
     */
    public Set<String> getEnrichmentServices() {
        return enrichmentServices.keySet();
    }

    /**
     * Returns an enrichment service
     * @param serviceName the name of the service
     * @return EnrichmentService, an implementation of EnrichmentService or null if not found
     */
    public EnrichmentService getEnrichmentService(String serviceName) {
        EnrichmentService service = enrichmentServices.get(serviceName);
        if (service != null) {
            return service;
        } else {
            if (enrichmentConfigs.containsKey(serviceName)) {
                initialize(enrichmentConfigs.get(serviceName));
                return enrichmentServices.get(serviceName);
            }
        }

        return null;
    }

}
