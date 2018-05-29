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
package org.nuxeo.ai.services;

import static java.util.Collections.singletonMap;
import static org.nuxeo.ai.AIConstants.AI_KIND_DIRECTORY;
import static org.nuxeo.ai.AIConstants.DEFAULT_BLOB_PROVIDER_PARAM;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_XP;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.ai.enrichment.EnrichmentDescriptor;
import org.nuxeo.ai.enrichment.EnrichmentService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolverService;
import org.nuxeo.ecm.directory.DirectoryEntryResolver;
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

    protected final Map<String, EnrichmentDescriptor> enrichmentConfigs = new HashMap<>();
    protected final Map<String, EnrichmentService> enrichmentServices = new HashMap<>();

    protected DirectoryEntryResolver kindResolver;

    /**
     * Get a blob provider id for the specified EnrichmentDescriptor
     */
    public static String getBlobProviderId(EnrichmentDescriptor descriptor) {
        String blobProviderId = descriptor.getBlobProviderId();
        if (StringUtils.isEmpty(blobProviderId)) {
            blobProviderId = Framework.getProperty(DEFAULT_BLOB_PROVIDER_PARAM);
        }
        return blobProviderId;
    }

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (ENRICHMENT_XP.equals(extensionPoint)) {
            EnrichmentDescriptor descriptor = (EnrichmentDescriptor) contribution;
            enrichmentConfigs.put(descriptor.name, descriptor);
        }
    }

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        getKindResolver();
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
            if (StringUtils.isEmpty(enrichmentService.getName()) || StringUtils.isEmpty(enrichmentService.getKind())) {
                throw new IllegalArgumentException(String.format("An enrichment service must be configured with a name %s and kind %s",
                                                                 descriptor.name, descriptor.getKind()));
            }

            if (!getKindResolver().validate(descriptor.getKind())) {
                throw new IllegalArgumentException(String.format("The %s kind for service %s must be defined in the %s vocabulary",
                                                                 descriptor.getKind(), descriptor.name, AI_KIND_DIRECTORY));
            }

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
     *
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

    /**
     * Gets a blob provider for the specified enrichment service.
     */
    public BlobProvider getBlobProviderForEnrichmentService(String serviceName) {
        EnrichmentDescriptor descriptor = enrichmentConfigs.get(serviceName);
        if (descriptor != null) {
            return Framework.getService(BlobManager.class).getBlobProvider(getBlobProviderId(descriptor));
        }

        throw new NuxeoException("Unknown enrichment service " + serviceName);
    }

    /**
     * Returns the DirectoryEntryResolver for the "aikind" directory.
     */
    protected DirectoryEntryResolver getKindResolver() {
        if (kindResolver == null) {
            ObjectResolverService objectResolverService = Framework.getService(ObjectResolverService.class);
            kindResolver =
                    (DirectoryEntryResolver) objectResolverService
                            .getResolver(DirectoryEntryResolver.NAME,
                                         singletonMap(DirectoryEntryResolver.PARAM_DIRECTORY, AI_KIND_DIRECTORY));
        }
        return kindResolver;
    }
}
