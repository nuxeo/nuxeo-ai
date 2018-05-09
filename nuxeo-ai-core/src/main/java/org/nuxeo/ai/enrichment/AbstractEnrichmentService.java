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
package org.nuxeo.ai.enrichment;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Basic implementation of an enrichment service with mimetype and max file size support.
 */
public abstract class AbstractEnrichmentService implements EnrichmentService {

    public static final String MAX_RESULTS = "maxResults";
    public static final String PREDICTION_MODEL_VERSION = "modelVersion";
    protected String name;
    protected String modelVersion;
    protected long maxSize;
    protected Set<String> supportedMimeTypes = new HashSet<>();

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        this.name = descriptor.name;
        this.maxSize = descriptor.maxSize;
        modelVersion = descriptor.options.getOrDefault(PREDICTION_MODEL_VERSION, name + ".1");
    }

    @Override
    public void addMimeTypes(Collection<String> mimeTypes) {
        supportedMimeTypes.addAll(mimeTypes);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean supportsMimeType(String mimeType) {
        return supportedMimeTypes.isEmpty() || supportedMimeTypes.contains(mimeType);
    }

    @Override
    public boolean supportsSize(long size) {
        return size <= maxSize;
    }

}
