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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.nuxeo.ai.services.PersistedConfigurationServiceImpl.KEY_VALUE_STORE;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.services.AIConfigurationService;
import org.nuxeo.ai.services.AIConfigurationServiceImpl;
import org.nuxeo.ai.services.PersistedConfigurationService;
import org.nuxeo.ai.services.PersistedConfigurationServiceImpl;
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
@Deploy("org.nuxeo.runtime.pubsub")
@RepositoryConfig(cleanup = Granularity.METHOD)
public class PersistedConfigurationServiceTest {

    protected List<String> messages = new CopyOnWriteArrayList<>();

    @Inject
    protected AIConfigurationService aiConfigurationService;

    @Inject
    protected PersistedConfigurationService pcs;

    protected volatile CountDownLatch messageReceivedLatch;

    protected static final String threshold = "<thresholdConfiguration type=\"File\"\n" + //
            "                            global=\"0.88\">\n" + //
            "      <thresholds>\n" + //
            "        <threshold xpath=\"dc:title\"\n" + //
            "                   value=\"0.75\"\n" + //
            "                   autofill=\"0.76\"\n" + //
            "                   autocorrect=\"0.77\"/>\n" + //
            "      </thresholds>\n" + //
            "    </thresholdConfiguration>";

    @After
    public void cleanUp() {
        PersistedConfigurationServiceImpl impl = (PersistedConfigurationServiceImpl) this.pcs;
        impl.clear();
    }

    @Test
    public void shouldRetrieveConfiguration() throws IOException {
        pcs.register(ThresholdConfiguratorDescriptor.class);

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

    @Test
    public void shouldGetAllConfigsByClass() throws IOException {
        String key = aiConfigurationService.set(threshold);
        assertThat(key).isNotBlank();

        List<ThresholdConfiguratorDescriptor> all = aiConfigurationService.getAll(
                ThresholdConfiguratorDescriptor.class);
        assertThat(all).hasSize(1);
    }

    @Test
    public void iCanPropagateConfiguration() throws InterruptedException {
        messageReceivedLatch = new CountDownLatch(1);
        aiConfigurationService.set(threshold);
        if (!messageReceivedLatch.await(5, TimeUnit.SECONDS)) {
            fail("message not received in 5s");
        }

        assertEquals(Collections.singletonList(AIConfigurationServiceImpl.TOPIC + "=" + threshold), messages);
    }

    protected KeyValueStore getStore() {
        KeyValueService service = Framework.getService(KeyValueService.class);
        return service.getKeyValueStore(KEY_VALUE_STORE);
    }
}
