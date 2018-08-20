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
 *     Pedro Cardoso
 */
package org.nuxeo.ai.model.publishing;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

public class ModelPublishingServiceImpl extends DefaultComponent implements ModelPublishingService {

    private static final String PUBLISHERS_XP = "publishers";

    protected final Map<String, ModelPublishingDescriptor> configs = new LinkedHashMap<>();

    protected final Map<String, ModelPublisherExtension> publishers = new HashMap<>();

    protected String defaultPublisher;

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (PUBLISHERS_XP.equals(extensionPoint)) {
            ModelPublishingDescriptor descriptor = (ModelPublishingDescriptor) contribution;
            this.configs.put(descriptor.id, descriptor);
            computeDefault();
        }
    }

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        configs.forEach((key, value) -> addPublisher(value));
    }

    protected void addPublisher(ModelPublishingDescriptor descriptor) {
        publishers.put(descriptor.id, descriptor.getInstance());
    }

    /**
     * Logic taken from UIDGeneratorComponent
     */
    protected void computeDefault() {
        String def = null;
        String last = null;
        for (ModelPublishingDescriptor contrib : configs.values()) {
            if (contrib.isDefault) {
                def = contrib.id;
            }
            last = contrib.id;
        }

        if (def == null) {
            def = last;
        }
        defaultPublisher = def;
    }

    @Override
    public void publishModel(String aiModelDocumentId, String publisherId) throws IOException {
        publishers.get(publisherId).publishModel(aiModelDocumentId);
    }

    @Override
    public void unpublishModel(String aiModelDocumentId, String publisherId) throws IOException {
        publishers.get(publisherId).unpublishModel(aiModelDocumentId);
    }

    @Override
    public boolean isModelPublished(String aiModelDocumentId, String publisherId) {
        return publishers.get(publisherId).isModelPublished(aiModelDocumentId);
    }

    @Override
    public void publishModel(String aiModelDocumentId) throws IOException {
        publishModel(aiModelDocumentId, defaultPublisher);
    }

    @Override
    public void unpublishModel(String aiModelDocumentId) throws IOException {
        unpublishModel(aiModelDocumentId, defaultPublisher);
    }

    @Override
    public boolean isModelPublished(String aiModelDocumentId) {
        return isModelPublished(aiModelDocumentId, defaultPublisher);
    }
}
