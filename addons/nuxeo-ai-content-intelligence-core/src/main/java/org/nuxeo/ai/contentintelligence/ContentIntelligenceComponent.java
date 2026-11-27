/*
 * (C) Copyright 2026 Hyland (http://hyland.com/) and others.
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
 */
package org.nuxeo.ai.contentintelligence;

import java.time.Duration;
import java.util.List;

import org.apache.commons.lang3.math.NumberUtils;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Holds the {@code knowledgeEnrichment} configurations and exposes the {@link ContentIntelligenceService}.
 *
 * @since 5.0.5
 */
public class ContentIntelligenceComponent extends DefaultComponent {

    public static final String XP_KNOWLEDGE_ENRICHMENT = "knowledgeEnrichment";

    /** Number of times the enrichment results are polled before giving up. */
    public static final String PULL_RESULTS_MAX_TRIES_PROP = "nuxeo.hyland.cic.pullResultsMaxTries";

    /** Interval, in milliseconds, between two polls of the enrichment results. */
    public static final String PULL_RESULTS_SLEEP_INTERVAL_PROP = "nuxeo.hyland.cic.pullResultsSleepInterval";

    public static final int DEFAULT_PULL_RESULTS_MAX_TRIES = 60;

    public static final int DEFAULT_PULL_RESULTS_SLEEP_INTERVAL = 3000;

    protected ContentIntelligenceServiceImpl service;

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        List<ContentIntelligenceDescriptor> descriptors = getDescriptors(XP_KNOWLEDGE_ENRICHMENT);
        int maxTries = NumberUtils.toInt(Framework.getProperty(PULL_RESULTS_MAX_TRIES_PROP),
                DEFAULT_PULL_RESULTS_MAX_TRIES);
        int sleepInterval = NumberUtils.toInt(Framework.getProperty(PULL_RESULTS_SLEEP_INTERVAL_PROP),
                DEFAULT_PULL_RESULTS_SLEEP_INTERVAL);
        service = new ContentIntelligenceServiceImpl(descriptors, maxTries, Duration.ofMillis(sleepInterval));
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        if (service != null) {
            service.close();
            service = null;
        }
        super.stop(context);
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if (adapter.isAssignableFrom(ContentIntelligenceService.class)) {
            return adapter.cast(service);
        }
        return super.getAdapter(adapter);
    }
}
