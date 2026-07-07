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
package org.nuxeo.ai.sns;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.client.utils.URIBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.aws.AWSClientFactory;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.SubscribeResponse;

/**
 * A component implementing {@link NotificationService}
 */
public class NotificationComponent extends DefaultComponent implements NotificationService {

    private static final Logger log = LogManager.getLogger(NotificationComponent.class);

    protected SnsClient amazonSNS;

    protected URI endpointURL;

    protected final Map<String, TopicDescriptor> topics = new HashMap<>();

    public static final String TOPICS_XP = "topics";

    @Override
    public void registerContribution(Object contribution, String xp, ComponentInstance component) {
        if (TOPICS_XP.equals(xp)) {
            TopicDescriptor topic = (TopicDescriptor) contribution;
            topics.put(topic.getType(), topic);
        } else {
            super.registerContribution(contribution, xp, component);
        }
    }

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        topics.forEach((name, topic) -> {
            log.debug("Subscribing to SNS topic {}", topic.getTopicArn());
            try {
                subscribe(topic.getTopicArn(), getURI(topic.getPath()));
            } catch (URISyntaxException e) {
                log.error("Failed to subscribe to {} with path {}", topic.getTopicArn(), topic.getPath(), e);
            } catch (Exception e) {
                log.warn("Could not subscribe to SNS topic {} (AWS may not be configured): {}",
                        topic.getTopicArn(), e.getMessage(), e);
            }
        });
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        amazonSNS = null;
    }

    @Override
    public SnsClient getClient() {
        return client();
    }

    @Override
    public String subscribe(String arn, URI uri) {
        SubscribeRequest request = SubscribeRequest.builder()
                                                   .topicArn(arn)
                                                   .protocol(uri.getScheme())
                                                   .endpoint(uri.toString())
                                                   .build();
        SubscribeResponse result = client().subscribe(request);
        return result.subscriptionArn();
    }

    @Override
    public String getTopicArnFor(String topicType) {
        if (!topics.containsKey(topicType)) {
            log.warn("Topic {} does not exist", topicType);
            return null;
        }
        return topics.get(topicType).getTopicArn();
    }

    @Override
    public int getApplicationStartedOrder() {
        return 500;
    }

    protected SnsClient client() {
        if (amazonSNS != null) {
            return amazonSNS;
        }

        synchronized (this) {
            if (amazonSNS == null) {
                amazonSNS = Framework.getService(AWSClientFactory.class).getSnsClient();
            }
            return amazonSNS;
        }
    }

    @Override
    public URI getURI(String path) throws URISyntaxException {
        if (endpointURL == null) {
            String host = (String) Framework.getProperties().getOrDefault("nuxeo.url", "http://0.0.0.0:8080");
            endpointURL = new URIBuilder(host).setPath(path).build();
            log.debug("Notifications will be received on {}", endpointURL);
        }

        return endpointURL;
    }
}
