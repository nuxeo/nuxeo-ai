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
package org.nuxeo.ai.metrics;

import org.nuxeo.runtime.metrics.MetricsService;

import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.SharedMetricRegistries;
import io.dropwizard.metrics5.Timer;

/**
 * Metrics class
 */
public class AIMetrics {

    protected final static MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());

    protected Timer insightPredictionTime;

    protected Timer insightPreConversionTime;

    /**
     * Method register metrics within shared registry of ${@link MetricRegistry}
     */
    public void register() {
        String basicName = getInsightName();
        // Insight
        insightPredictionTime = registry.timer(MetricRegistry.name(basicName, "insightPredictionTime"));
        insightPreConversionTime = registry.timer(MetricRegistry.name(basicName, "insightPreConversionTime"));
    }

    public Timer getInsightPredictionTime() {
        return insightPredictionTime;
    }

    public Timer getInsightPreConversionTime() {
        return insightPreConversionTime;
    }

    /**
     * Method removes all class defined metrics from shared registry of ${@link MetricRegistry}
     */
    public void unregister() {
        String insightName = getInsightName();
        registry.removeMatching((name, metrics) -> name.toString().startsWith(insightName));
    }

    protected String getInsightName() {
        return MetricRegistry.name("nuxeo.ai", "insight").toString();
    }

}
