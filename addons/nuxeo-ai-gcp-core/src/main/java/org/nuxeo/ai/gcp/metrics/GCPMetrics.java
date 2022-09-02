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
package org.nuxeo.ai.gcp.metrics;

import org.nuxeo.runtime.metrics.MetricsService;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

import io.dropwizard.metrics5.Counter;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.SharedMetricRegistries;

/**
 * GCP Metrics service
 */
public class GCPMetrics extends DefaultComponent {

    protected final static MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());

    protected Counter visionGlobalCalls;

    protected Counter cropHintsCalls;

    protected Counter faceCalls;

    protected Counter imagePropertiesCalls;

    protected Counter labelsCalls;

    protected Counter landmarkCalls;

    protected Counter logoCalls;

    protected Counter objectLocalizationCalls;

    protected Counter textCalls;
    
    protected Counter safeSearchCalls;

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
        String visionName = getVisionName();

        // Vision
        visionGlobalCalls = registry.counter(MetricRegistry.name(visionName, "visionGlobalCalls"));

        // Charges are incurred per image.
        // For files with multiple pages, such as PDF files, each page is treated as an individual image.
        // TODO AICORE-446
        faceCalls = registry.counter(MetricRegistry.name(visionName, "faceCalls"));
        imagePropertiesCalls = registry.counter(MetricRegistry.name(visionName, "imagePropertiesCalls"));
        labelsCalls = registry.counter(MetricRegistry.name(visionName, "labelsCalls"));
        landmarkCalls = registry.counter(MetricRegistry.name(visionName, "landmarkCalls"));
        logoCalls = registry.counter(MetricRegistry.name(visionName, "logoCalls"));
        objectLocalizationCalls = registry.counter(MetricRegistry.name(visionName, "objectLocalizationCalls"));
        textCalls = registry.counter(MetricRegistry.name(visionName, "textCalls"));
        cropHintsCalls = registry.counter(MetricRegistry.name(visionName, "cropHintsCalls"));
        safeSearchCalls = registry.counter(MetricRegistry.name(visionName, "safeSearchCalls"));
    }

    /**
     * Method removes all class defined metrics from shared registry of ${@link MetricRegistry}
     */
    public void unregister() {
        String basicName = getGCPName();
        registry.removeMatching((name, metrics) -> name.toString().startsWith(basicName));
    }

    protected String getGCPName(String... names) {
        return MetricRegistry.name("nuxeo.ai.gcp", names).toString();
    }

    protected String getVisionName() {
        return getGCPName("vision");
    }

    public Counter getFaceCalls() {
        return faceCalls;
    }

    public Counter getCropHintsCalls() {
        return cropHintsCalls;
    }

    public Counter getSafeSearchCalls() {
        return safeSearchCalls;
    }

    public Counter getVisionGlobalCalls() {
        return visionGlobalCalls;
    }

    public Counter getImagePropertiesCalls() {
        return imagePropertiesCalls;
    }

    public Counter getLabelsCalls() {
        return labelsCalls;
    }

    public Counter getLandmarkCalls() {
        return landmarkCalls;
    }

    public Counter getLogoCalls() {
        return logoCalls;
    }

    public Counter getObjectLocalizationCalls() {
        return objectLocalizationCalls;
    }

    public Counter getTextCalls() {
        return textCalls;
    }

}
