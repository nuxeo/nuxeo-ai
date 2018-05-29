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

import static org.nuxeo.ai.AIComponent.DEFAULT_BLOB_PROVIDER_PARAM;

import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.pipes.services.PipelineServiceImpl;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.SimpleFeature;

/**
 * Sets the configuration for Enrichment tests
 */
@Deploy({"org.nuxeo.runtime.stream", "org.nuxeo.runtime.stream.pipes.nuxeo-pipes",
        "org.nuxeo.ecm.default.config",
        "org.nuxeo.ai.ai-core:OSGI-INF/enrichment-stream-config-test.xml",
        "org.nuxeo.ai.ai-core"})
public class EnrichmentTestFeature extends SimpleFeature {

    public static final String PIPES_TEST_CONFIG = "test_enrichment";

    @Override
    public void beforeRun(FeaturesRunner runner) throws Exception {
        super.beforeRun(runner);
        Framework.getProperties().put(PipelineServiceImpl.PIPES_CONFIG, PIPES_TEST_CONFIG);
        Framework.getProperties().put(DEFAULT_BLOB_PROVIDER_PARAM, "test");
    }

}
