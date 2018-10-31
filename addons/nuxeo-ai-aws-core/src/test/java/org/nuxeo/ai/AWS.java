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

import org.junit.Assume;

/**
 * Works with AWS tests
 */
public class AWS {

    public static final String PROVIDES_AWS_CREDENTIALS = "provides.aws.credentials";

    private AWS() {
    }

    /**
     * Assume we have valid credentials
     */
    public static void assumeCredentials() {
        Assume.assumeTrue("AWS Credentials not set", Boolean.getBoolean(PROVIDES_AWS_CREDENTIALS));
    }
}
