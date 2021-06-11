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
package org.nuxeo.ai;

import static org.junit.Assume.assumeTrue;

import org.apache.commons.lang3.StringUtils;
import com.amazonaws.SDKGlobalConfiguration;

/**
 * Works with AWS tests
 */
public class AWS {

    /**
     * Assume we have valid credentials
     */
    public static void assumeCredentials() {
        String envId = StringUtils.defaultIfBlank(System.getenv(SDKGlobalConfiguration.ACCESS_KEY_ENV_VAR),
                System.getenv(SDKGlobalConfiguration.ALTERNATE_ACCESS_KEY_ENV_VAR));
        String envSecret = StringUtils.defaultIfBlank(System.getenv(SDKGlobalConfiguration.SECRET_KEY_ENV_VAR),
                System.getenv(SDKGlobalConfiguration.ALTERNATE_SECRET_KEY_ENV_VAR));
        assumeTrue("AWS Credentials not set in the environment variables", StringUtils.isNoneBlank(envId, envSecret));
    }
}
