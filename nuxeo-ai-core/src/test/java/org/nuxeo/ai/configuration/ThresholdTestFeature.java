/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.configuration;

import static org.nuxeo.ai.configuration.ThresholdComponent.DEFAULT_THRESHOLD_VALUE;

import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RunnerFeature;

@Deploy({"org.nuxeo.runtime.stream",
        "org.nuxeo.ai.nuxeo-ai-pipes",
        "org.nuxeo.ecm.default.config",
        "org.nuxeo.ai.ai-core"})
public class ThresholdTestFeature implements RunnerFeature {

    @Override
    public void beforeRun(FeaturesRunner runner) throws Exception {
        Framework.getProperties().put(DEFAULT_THRESHOLD_VALUE, "0.99");
    }
}
