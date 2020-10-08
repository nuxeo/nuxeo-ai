/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.nuxeo.ai.services.PersistedConfigurationServiceImpl.KEY_VALUE_STORE;

import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.services.PersistedConfigurationService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;
import org.nuxeo.runtime.model.Descriptor;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ CoreFeature.class })
@Deploy({ "org.nuxeo.ai.ai-core" })
@RepositoryConfig(cleanup = Granularity.METHOD)
public class PersistedConfigurationServiceTest {

    @Test
    public void shouldRetrieveConfiguration() throws IOException {
        PersistedConfigurationService pcs = Framework.getService(PersistedConfigurationService.class);
        assertThat(pcs).isNotNull();

        pcs.register(ThresholdConfiguratorDescriptor.class);

        String threshold = "<thresholdConfiguration type=\"File\"\n" + //
                "                            global=\"0.88\">\n" + //
                "      <thresholds>\n" + //
                "        <threshold xpath=\"dc:title\"\n" + //
                "                   value=\"0.75\"\n" + //
                "                   autofill=\"0.76\"\n" + //
                "                   autocorrect=\"0.77\"/>\n" + //
                "      </thresholds>\n" + //
                "    </thresholdConfiguration>";

        getStore().put("testKey", threshold);

        Descriptor descriptor = pcs.retrieve("testKey");
        assertThat(descriptor).isNotNull().isInstanceOf(ThresholdConfiguratorDescriptor.class);

        ThresholdConfiguratorDescriptor tcd = (ThresholdConfiguratorDescriptor) descriptor;
        assertThat(tcd.getGlobal()).isEqualTo(0.88f);

        tcd.global = 0.77f;

        pcs.persist("testKey", tcd);
        ThresholdConfiguratorDescriptor updated = (ThresholdConfiguratorDescriptor) pcs.retrieve("testKey");
        assertThat(updated.getGlobal()).isEqualTo(0.77f);
    }

    protected KeyValueStore getStore() {
        KeyValueService service = Framework.getService(KeyValueService.class);
        return service.getKeyValueStore(KEY_VALUE_STORE);
    }
}
