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
import static org.nuxeo.ai.enrichment.EnrichmentUtils.PICTURE_RESIZE_CONVERTER;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.bulk.RecordWriter;
import org.nuxeo.ai.bulk.RecordWriterDescriptor;
import org.nuxeo.ai.enrichment.EnrichmentDescriptor;
import org.nuxeo.ai.enrichment.EnrichmentService;
import org.nuxeo.ai.enrichment.EnrichmentSupport;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.convert.api.ConverterCheckResult;
import org.nuxeo.ecm.core.convert.api.ConverterNotRegistered;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolverService;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.ecm.directory.DirectoryEntryResolver;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeEntry;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;
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

    private static final Log log = LogFactory.getLog(AIComponent.class);

    protected final Map<String, EnrichmentDescriptor> enrichmentConfigs = new HashMap<>();

    protected final List<RecordWriterDescriptor> recordWriterDescriptors = new ArrayList<>();

    protected final Map<String, EnrichmentService> enrichmentServices = new HashMap<>();

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
    public void start(ComponentContext context) {
        super.start(context);
        getKindResolver();
        enrichmentConfigs.values().forEach(descriptor -> {
            if (!enrichmentServices.containsKey(descriptor.name)) {
                initialize(descriptor);
            }
        });
        recordWriterDescriptors.forEach(
                descriptor -> descriptor.getNames().forEach(n -> writers.put(n, descriptor.getWriter(n))));
        if (log.isDebugEnabled()) {
            log.debug("AIComponent has started.");
        }

        try {
            ConverterCheckResult pictureResize = Framework.getService(ConversionService.class)
                                                          .isConverterAvailable(PICTURE_RESIZE_CONVERTER);
        } catch (ConverterNotRegistered e) {
            log.warn(PICTURE_RESIZE_CONVERTER + " converter is not registered.  You will not be able to export images.");
        }
    }

    /**
     * Initialize an enrichment descriptor
     */
    public synchronized void initialize(EnrichmentDescriptor descriptor) {
        if (!enrichmentServices.containsKey(descriptor.name)) {
            MimetypeRegistry mimeRegistry = Framework.getService(MimetypeRegistry.class);
            EnrichmentService enrichmentService = descriptor.getService();
            if (StringUtils.isEmpty(enrichmentService.getName()) || StringUtils.isEmpty(enrichmentService.getKind())) {
                throw new IllegalArgumentException(
                        String.format("An enrichment service must be configured with a name %s and kind %s",
                                      descriptor.name, descriptor.getKind()));
            }

            if (!getKindResolver().validate(descriptor.getKind())) {
                throw new IllegalArgumentException(
                        String.format("The %s kind for service %s must be defined in the %s vocabulary",
                                      descriptor.getKind(), descriptor.name, AI_KIND_DIRECTORY));
            }

            if (enrichmentService instanceof EnrichmentSupport) {
                List<String> mimeTypes = new ArrayList<>();
                descriptor.getMimeTypes().forEach(mimeType -> {
                    if (mimeType.normalized) {
                        MimetypeEntry entry = mimeRegistry.getMimetypeEntryByMimeType(mimeType.name);
                        mimeTypes.addAll(entry.getMimetypes());
                    } else {
                        mimeTypes.add(mimeType.name);
                    }
                });
                ((EnrichmentSupport) enrichmentService).addMimeTypes(mimeTypes);
            }
            addEnrichmentService(descriptor.name, enrichmentService);
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
     * Add an enrichment service
     *
     * @param serviceName the name of the service
     */
    public void addEnrichmentService(String serviceName, EnrichmentService service) {
        enrichmentServices.put(serviceName, service);
    }

    /**
     * Gets a TransientStore for the specified enrichment service.
     */
    public TransientStore getTransientStoreForEnrichmentService(String serviceName) {
        EnrichmentDescriptor descriptor = enrichmentConfigs.get(serviceName);
        if (descriptor != null) {
            return Framework.getService(TransientStoreService.class).getStore(descriptor.getTransientStoreName());
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

    /**
     * Get a record writer by name
     */
    public RecordWriter getRecordWriter(String name) {
        return writers.get(name);
    }
}
