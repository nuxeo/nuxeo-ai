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
package org.nuxeo.ai.cloud;

import java.time.Duration;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.runtime.model.Descriptor;

@XObject("config")
public class CloudConfigDescriptor implements Descriptor {

    @XNode("@id")
    protected String id;

    @XNode("@url")
    protected String url = "http://localhost:8080/nuxeo";

    @XNode("@readTimeout")
    protected Duration readTimeout = Duration.ofMinutes(10);  // Default 10 minutes

    @XNode("@connectTimeout")
    protected Duration connectTimeout = Duration.ofSeconds(30);  // Default 30 seconds

    @XNode("@writeTimeout")
    protected Duration writeTimeout = Duration.ofMinutes(10);  // Default 10 minutes

    @XNode("@projectId")
    protected String projectId;

    @XNode("authentication")
    protected Authentication authentication;

    @Override
    public String getId() {
        return id;
    }

    @XObject("authentication")
    public static class Authentication {

        @XNode("@token")
        protected String token;

        @XNode("@username")
        protected String username;

        @XNode("@password")
        protected String password;

        public String getToken() {
            return token;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }

    public String getUrl() {
        return url;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    public String getProjectId() {
        return projectId;
    }

    public Authentication getAuthentication() {
        return authentication;
    }
}
