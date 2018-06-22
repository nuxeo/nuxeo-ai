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
package org.nuxeo.aws.credentials;

import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;

/**
 * See DefaultAWSCredentialsProviderChain
 */
public class NuxeoCredentialsProviderChain extends AWSCredentialsProviderChain {

    private static final NuxeoCredentialsProviderChain INSTANCE = new NuxeoCredentialsProviderChain();

    public NuxeoCredentialsProviderChain() {
        super(new NuxeoCredentialsUsingProperties(),
              new EnvironmentVariableCredentialsProvider(),
              new SystemPropertiesCredentialsProvider(),
              new ProfileCredentialsProvider(),
              new EC2ContainerCredentialsProviderWrapper()
        );
    }

    public static NuxeoCredentialsProviderChain getInstance() {
        return INSTANCE;
    }
}