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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.SdkClientException;
import com.amazonaws.regions.AwsEnvVarOverrideRegionProvider;
import com.amazonaws.regions.AwsProfileRegionProvider;
import com.amazonaws.regions.AwsRegionProvider;
import com.amazonaws.regions.AwsSystemPropertyRegionProvider;
import com.amazonaws.regions.InstanceMetadataRegionProvider;

/**
 * Add NuxeoRegionUsingProperties to the start of the chain.
 * If no region is found in the chain then it defaults to "us-east-1".
 * Some of this code was taken from DefaultAwsRegionProviderChain V2 (without the V1 bug)
 * and it reuses providers in the same way as AWSCredentialsProviderChain
 */
public class NuxeoRegionProviderChain extends AwsRegionProvider {
    private static final Log log = LogFactory.getLog(NuxeoRegionProviderChain.class);
    private static final AwsRegionProvider INSTANCE = new NuxeoRegionProviderChain(true);
    private final List<AwsRegionProvider> providers;
    private final boolean reuseLastProvider;
    private AwsRegionProvider lastUsedProvider;

    public NuxeoRegionProviderChain(boolean reUse) {
        this(reUse,
             new NuxeoRegionUsingProperties(),
             new AwsEnvVarOverrideRegionProvider(),
             new AwsSystemPropertyRegionProvider(),
             new AwsProfileRegionProvider(),
             new InstanceMetadataRegionProvider(),
             new DefaultRegionProvider()
        );
    }

    public NuxeoRegionProviderChain(boolean reuseLastProvider, AwsRegionProvider... providers) {
        this.reuseLastProvider = reuseLastProvider;
        this.providers = new ArrayList<>(providers.length);
        Collections.addAll(this.providers, providers);
    }

    public static AwsRegionProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public String getRegion() throws SdkClientException {
        if (reuseLastProvider && lastUsedProvider != null) {
            return lastUsedProvider.getRegion();
        }

        List<String> exceptionMessages = null;

        for (AwsRegionProvider provider : providers) {
            try {
                String region = provider.getRegion();
                if (region != null) {
                    lastUsedProvider = provider;
                    if (exceptionMessages != null) {
                        log.warn(String.format("Using %s but there were errors in region provider chain : %s",
                                               region, exceptionMessages));
                    }
                    return region;
                }
            } catch (Exception e) {
                // Ignore any exceptions and move onto the next provider
                String message = provider.toString() + ": " + e.getMessage();
                if (exceptionMessages == null) {
                    exceptionMessages = new ArrayList<>();
                }
                exceptionMessages.add(message);
            }
        }

        throw new SdkClientException("Unable to load region from any of the providers in the chain " + this
                                             + ": " + exceptionMessages);
    }

    private static class DefaultRegionProvider extends AwsRegionProvider {
        @Override
        public String getRegion() {
            return "us-east-1";
        }
    }
}
