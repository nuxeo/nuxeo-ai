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

import org.nuxeo.ai.bulk.RecordWriter;
import org.nuxeo.ai.bulk.RecordWriterDescriptor;
import org.nuxeo.ai.enrichment.EnrichmentDescriptor;
import org.nuxeo.ai.enrichment.EnrichmentProvider;
import org.nuxeo.ai.enrichment.EnrichmentSupport;
import org.nuxeo.ai.metrics.AIMetrics;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolverService;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.ecm.directory.DirectoryEntryResolver;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeEntry;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.Component;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides one or more services using AI
 */
public class AIComponent extends DefaultComponent {

    public static final String ENRICHMENT_XP = "enrichment";

    public static final String RECORDWRITER_XP = "recordWriter";

    protected final AIMetrics metrics = new AIMetrics();

    private static final Log log = LogFactory.getLog(AIComponent.class);

    protected final Map<String, EnrichmentDescriptor> enrichmentConfigs = new HashMap<>();

    protected final List<RecordWriterDescriptor> recordWriterDescriptors = new ArrayList<>();

    protected final Map<String, EnrichmentProvider> enrichmentProviders = new HashMap<>();

    protected final Map<String, RecordWriter> writers = new HashMap<>();

    protected DirectoryEntryResolver kindResolver;

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (ENRICHMENT_XP.equals(extensionPoint)) {
            EnrichmentDescriptor descriptor = (EnrichmentDescriptor) contribution;
            enrichmentConfigs.put(descriptor.name, descriptor);
        } else if (RECORDWRITER_XP.equals(extensionPoint)) {
            RecordWriterDescriptor descriptor = (RecordWriterDescriptor) contribution;
            recordWriterDescriptors.add(descriptor);
        }
    }

    @Override
    public int getApplicationStartedOrder() {
        Component component = (Component) Framework.getRuntime()
                                                   .getComponent(
                                                           "org.nuxeo.ecm.core.convert.service.ConversionServiceImpl");
        if (component == null) {
            // TF Writers are using the conversion service when starting up
            return super.getApplicationStartedOrder() + 1;
        }
        return component.getApplicationStartedOrder() + 1;
    }

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        metrics.register();
        getKindResolver();
        enrichmentConfigs.values()
                         .forEach(descriptor -> {
                             if (!enrichmentProviders.containsKey(descriptor.name)) {
                                 initialize(descriptor);
                             }
                         });
        recordWriterDescriptors.forEach(
                descriptor -> descriptor.getNames()
                                        .forEach(n -> writers.put(n, descriptor.getWriter(n))));
        if (log.isDebugEnabled()) {
            writers.entrySet()
                   .forEach(e -> log.debug("Adding a record writer: " + e.toString()));
            log.debug("AIComponent has started.");
        }
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        metrics.unregister();
    }

    /**
     * @return the AI metrics monitoring
     */
    public AIMetrics getMetrics() {
        return metrics;
    }

    /**
     * Initialize an enrichment descriptor
     */
    public synchronized void initialize(EnrichmentDescriptor descriptor) {
        if (!enrichmentProviders.containsKey(descriptor.name)) {
            MimetypeRegistry mimeRegistry = Framework.getService(MimetypeRegistry.class);
            EnrichmentProvider enrichmentProvider = descriptor.getProvider();
            if (StringUtils.isEmpty(enrichmentProvider.getName()) || StringUtils.isEmpty(
                    enrichmentProvider.getKind())) {
                throw new IllegalArgumentException(
                        String.format("An enrichment provider must be configured with a name %s and kind %s",
                                descriptor.name, descriptor.getKind()));
            }

            if (!getKindResolver().validate(descriptor.getKind())) {
                throw new IllegalArgumentException(
                        String.format("The %s kind for provider %s must be defined in the %s vocabulary",
                                descriptor.getKind(), descriptor.name, AI_KIND_DIRECTORY));
            }

            if (enrichmentProvider instanceof EnrichmentSupport) {
                List<String> mimeTypes = new ArrayList<>();
                descriptor.getMimeTypes()
                          .forEach(mimeType -> {
                              if (mimeType.normalized) {
                                  MimetypeEntry entry = mimeRegistry.getMimetypeEntryByMimeType(mimeType.name);
                                  mimeTypes.addAll(entry.getMimetypes());
                              } else {
                                  mimeTypes.add(mimeType.name);
                              }
                          });
                ((EnrichmentSupport) enrichmentProvider).addMimeTypes(mimeTypes);
            }
            addEnrichmentProvider(descriptor.name, enrichmentProvider);
        }
    }

    /**
     * Returns the names of all the enrichment services that are configured
     */
    public Set<String> getEnrichmentProviders() {
        return enrichmentProviders.keySet();
    }

    /**
     * Returns an enrichment provider
     *
     * @param provider the name of the provider
     * @return EnrichmentProvider, an implementation of EnrichmentProvider or null if not found
     */
    public EnrichmentProvider getEnrichmentProvider(String provider) {
        EnrichmentProvider service = enrichmentProviders.get(provider);
        if (service != null) {
            return service;
        } else {
            if (enrichmentConfigs.containsKey(provider)) {
                initialize(enrichmentConfigs.get(provider));
                return enrichmentProviders.get(provider);
            }
        }

        return null;
    }

    /**
     * Add an enrichment provider
     *
     * @param providerName the name of the provider
     */
    public void addEnrichmentProvider(String providerName, EnrichmentProvider provider) {
        enrichmentProviders.put(providerName, provider);
        if (log.isDebugEnabled()) {
            log.debug("Adding enrichment provider " + providerName);
        }
    }

    /**
     * Gets a TransientStore for the specified enrichment provider.
     */
    public TransientStore getTransientStoreForEnrichmentProvider(String providerName) {
        EnrichmentDescriptor descriptor = enrichmentConfigs.get(providerName);
        if (descriptor != null) {
            return Framework.getService(TransientStoreService.class)
                            .getStore(descriptor.getTransientStoreName());
        }
        throw new IllegalArgumentException("Unknown enrichment provider " + providerName);
    }

    /**
     * Returns the DirectoryEntryResolver for the "aikind" directory.
     */
    protected DirectoryEntryResolver getKindResolver() {
        if (kindResolver == null) {
            ObjectResolverService objectResolverService = Framework.getService(ObjectResolverService.class);
            kindResolver = (DirectoryEntryResolver) objectResolverService.getResolver(DirectoryEntryResolver.NAME,
                    singletonMap(DirectoryEntryResolver.PARAM_DIRECTORY, AI_KIND_DIRECTORY));
        }
        return kindResolver;
    }

    /**
     * Get a record writer by name
     */
    public RecordWriter getRecordWriter(String name) {
        return writers.get(name);
    }
}
