/*
 * (C) Copyright 2026 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.sns.NotificationService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * Verifies that AWSClientFactory reports available when AWS credentials/region are configured.
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy("org.nuxeo.runtime.aws")
@Deploy("org.nuxeo.ai.aws.aws-core")
@Deploy("org.nuxeo.ai.aws.aws-core:OSGI-INF/test-aws-config-valid-region.xml")
public class TestAWSClientFactoryConfigured {

    @Inject
    protected AWSClientFactory clientFactory;

    @Inject
    protected NotificationService notificationService;

    @Test
    public void factoryIsAvailableWithConfig() {
        assertThat(clientFactory).isNotNull();
        assertThat(clientFactory.isAvailable()).isTrue();
    }

    @Test
    public void canCreateAllClientsWhenConfigured() {
        assertThat(clientFactory.getComprehendClient()).isNotNull();
        assertThat(clientFactory.getRekognitionClient()).isNotNull();
        assertThat(clientFactory.getTextractClient()).isNotNull();
        assertThat(clientFactory.getTranslateClient()).isNotNull();
        assertThat(clientFactory.getTranscribeClient()).isNotNull();
        assertThat(clientFactory.getSnsClient()).isNotNull();
    }

    @Test
    public void factoryResolvesRegionWhenClientRequestedBeforeStart() {
        // e.g. a component with a lower start order asking for a client before the factory is started
        AWSClientFactory notStarted = new AWSClientFactory();
        assertThat(notStarted.isAvailable()).isFalse();
        assertThat(notStarted.getSnsClient()).isNotNull();
        assertThat(notStarted.isAvailable()).isTrue();
    }

    @Test
    public void clientNotCreatedWhenStoppedWhileWaitingForCreation() throws InterruptedException {
        AWSClientFactory factory = new AWSClientFactory();
        assertThat(factory.getComprehendClient()).isNotNull(); // factory is now available
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                factory.getSnsClient();
            } catch (NuxeoException e) {
                error.set(e);
            }
        });
        synchronized (factory) {
            // the caller passes the availability check, then waits for the lock to create the client
            caller.start();
            long deadline = System.currentTimeMillis() + 5000;
            while (caller.getState() != Thread.State.BLOCKED && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertThat(caller.getState()).isEqualTo(Thread.State.BLOCKED);
            factory.stop(null);
        }
        caller.join(5000);
        assertThat(error.get()).isInstanceOf(NuxeoException.class);
    }

    @Test
    public void stoppedFactoryDoesNotCreateClients() throws InterruptedException {
        AWSClientFactory stopped = new AWSClientFactory();
        stopped.stop(null);
        assertThatThrownBy(stopped::getSnsClient).isInstanceOf(NuxeoException.class);
    }

    @Test
    public void notificationServiceCanGetClientWhenConfigured() {
        assertThat(notificationService.getClient()).isNotNull();
    }
}
