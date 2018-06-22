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

import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.runtime.api.Framework;

import com.amazonaws.SdkClientException;
import com.amazonaws.regions.AwsRegionProvider;

/**
 * An AwsRegionProvider using properties from nuxeo.conf
 */
public class NuxeoRegionUsingProperties extends AwsRegionProvider {

    public static final String AWS_REGION = "nuxeo.s3storage.region";

    protected String awsRegion;

    public NuxeoRegionUsingProperties() {
        init(Framework.getProperties());
    }

    protected void init(Properties properties) {
        String region = properties.getProperty(AWS_REGION);
        if (StringUtils.isNotBlank(awsRegion)) {
            awsRegion = region;
        }
    }

    @Override
    public String getRegion() throws SdkClientException {
        return awsRegion;
    }
}
