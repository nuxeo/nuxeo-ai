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
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.SubscribeRequest;

/**
 * A component implementing {@link NotificationService}
 */
public class NotificationComponent extends DefaultComponent implements NotificationService {

    private static final Logger log = LogManager.getLogger(NotificationComponent.class);

    protected AmazonSNS amazonSNS;

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
            log.debug("Creating SNS topic {}", topic.getName());
            CreateTopicResult result = createTopic(topic.name);
            topic.setArn(result.getTopicArn());

            try {
                subscribe(result.getTopicArn(), getURL(topic.getPath()));
            } catch (URISyntaxException e) {
                log.error(e);
            }
        });
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        amazonSNS = null;
    }


    @Override
    public AmazonSNS getClient() {
        return client();
    }

    @Override
    public CreateTopicResult createTopic(String name) {
        final CreateTopicRequest request = new CreateTopicRequest(name);
        return client().createTopic(request);
    }

    @Override
    public void removeTopic(String name) {
        if (topics.containsKey(name)) {
            client().deleteTopic(topics.get(name).arn);
        } else {
            log.info("An attempt to remove non-existent topic with name " + name);
        }
    }

    @Override
    public void subscribe(String arn, URI uri) {
        SubscribeRequest https = new SubscribeRequest(arn, uri.getScheme(), uri.toString());
        client().subscribe(https);
    }

    @Override
    public String getTopicArnFor(String topicType) {
        if (!topics.containsKey(topicType)) {
            return null;
        }
        return topics.get(topicType).getArn();
    }

    @Override
    public int getApplicationStartedOrder() {
        return 500;
    }

    protected AmazonSNS client() {
        if (amazonSNS != null) {
            return amazonSNS;
        }

        synchronized (this) {
            amazonSNS = AmazonSNSClientBuilder.standard()
                    .withCredentials(AWSHelper.getInstance().getCredentialsProvider())
                    .withRegion(AWSHelper.getInstance().getRegion())
                    .build();
            return amazonSNS;
        }
    }

    protected URI getURL(String path) throws URISyntaxException {
        if (endpointURL == null) {
            String host = (String) Framework.getProperties().getOrDefault("nuxeo.url", "http://0.0.0.0:8080");
            endpointURL = new URIBuilder(host).setPath(path).build();
            log.debug("Notifications will be received on {}", endpointURL);
        }

        return endpointURL;
    }
}
