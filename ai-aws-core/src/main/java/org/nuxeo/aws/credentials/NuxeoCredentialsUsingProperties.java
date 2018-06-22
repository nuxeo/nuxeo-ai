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

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Properties;

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;

/**
 * An AWSCredentialsProvider using properties from nuxeo.conf
 */
public class NuxeoCredentialsUsingProperties implements AWSCredentialsProvider {

    public static final String AWS_ID = "nuxeo.s3storage.awsid";
    public static final String AWS_SECRET = "nuxeo.s3storage.awssecret";
    public static final String AWS_SESSION_TOKEN = "nuxeo.s3storage.awssessiontoken";

    protected AWSCredentials awsCredentials;

    public NuxeoCredentialsUsingProperties() {
        init(Framework.getProperties());
    }

    protected void init(Properties properties) {
        String awsId = properties.getProperty(AWS_ID);
        String awsSecret = properties.getProperty(AWS_SECRET);
        String awsSessionToken = properties.getProperty(AWS_SESSION_TOKEN);

        if (isNotBlank(awsId) && isNotBlank(awsSecret)) {
            if (isNotBlank(awsSessionToken)) {
                awsCredentials = new BasicSessionCredentials(awsId, awsSecret, awsSessionToken);
            } else {
                awsCredentials = new BasicAWSCredentials(awsId, awsSecret);
            }
        }
    }

    @Override
    public AWSCredentials getCredentials() {
        if (awsCredentials == null) {
            throw new NuxeoException("Unable to configure aws credentials");
        }
        return awsCredentials;
    }

    @Override
    public void refresh() {
        init(Framework.getProperties());
    }
}
