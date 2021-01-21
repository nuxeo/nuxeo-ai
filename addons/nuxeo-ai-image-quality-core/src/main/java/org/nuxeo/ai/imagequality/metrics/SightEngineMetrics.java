/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo
 */
package org.nuxeo.ai.imagequality.metrics;

import org.nuxeo.runtime.metrics.MetricsService;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;

/**
 * SightEngine Metrics service
 */
public class SightEngineMetrics extends DefaultComponent {

    protected final static MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());

    protected Counter sightEngineCalls;

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        this.register();
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        this.unregister();
    }

    /**
     * Method register metrics within shared registry of ${@link MetricRegistry}
     */
    public void register() {
        String sightEngineName = getSighEngineName();
        sightEngineCalls = registry.counter(MetricRegistry.name(sightEngineName, "visionGlobalCalls"));
    }

    /**
     * Method removes all class defined metrics from shared registry of ${@link MetricRegistry}
     */
    public void unregister() {
        String basicName = getSighEngineName();
        registry.removeMatching((name, metrics) -> name.startsWith(basicName));
    }

    protected String getSighEngineName(String... names) {
        return MetricRegistry.name("nuxeo.ai.sightengine", names);
    }

    public Counter getSightEngineCalls() {
        return sightEngineCalls;
    }
}
