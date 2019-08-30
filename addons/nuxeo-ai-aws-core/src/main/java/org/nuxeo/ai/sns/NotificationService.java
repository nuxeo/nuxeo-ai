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

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.CreateTopicResult;

/**
 * A service responsible for registering and creating and using AWS SNS topics
 */
public interface NotificationService {

    /**
     * @return AWS SNS client
     */
    AmazonSNS getClient();

    /**
     * Creates topic with
     * @param name of topic to be created
     * @return {@link CreateTopicResult}
     */
    CreateTopicResult createTopic(String name);

    /**
     * Subscribes for a topic with
     * @param arn of before created topic
     * @param uri to receive {@link Notification} uri must be either http or https
     *            if using it on localhost set to http://0.0.0.0 using http://localhost will through an exception
     */
    void subscribe(String arn, URI uri);

    /**
     * Provides with ARN of a topic with
     * @param topicType
     * @return ARN as a {@link String}
     */
    String getTopicArnFor(String topicType);

    /**
     * Removes topic with
     * @param name from registry and from SNS
     */
    void removeTopic(String name);
}
