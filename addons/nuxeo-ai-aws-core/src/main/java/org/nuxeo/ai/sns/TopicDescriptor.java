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

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;

/**
 * Describes SNS topic to be created from Nuxeo
 */
@XObject("topic")
public class TopicDescriptor {

    /**
     * topic's name. Will be used as id on SNS
     */
    @XNode("@name")
    protected String name;

    /**
     * topic's type. Used internally for separating responsibilities among services
     */
    @XNode("@type")
    protected String type;

    /**
     * endpoint path that will be combines with host name. Used for SNS subscription
     */
    @XNode("@path")
    protected String path;

    /**
     * ARN of the topic. Derived on runtime
     */
    protected String arn;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getArn() {
        return arn;
    }

    public void setArn(String arn) {
        this.arn = arn;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String url) {
        this.path = url;
    }
}
