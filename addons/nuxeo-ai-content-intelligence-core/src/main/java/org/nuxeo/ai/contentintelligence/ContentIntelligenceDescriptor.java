/*
 * (C) Copyright 2026 Hyland (http://hyland.com/) and others.
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
 */
package org.nuxeo.ai.contentintelligence;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.runtime.model.Descriptor;

/**
 * Connection settings of a Hyland Content Intelligence Knowledge Enrichment configuration, contributed to the
 * {@code knowledgeEnrichment} extension point of {@link ContentIntelligenceService}. The element names are the ones
 * of the Content Intelligence connector, so an existing contribution only needs a new {@code target}.
 *
 * @since 5.0.5
 */
@XObject("knowledgeEnrichment")
public class ContentIntelligenceDescriptor implements Descriptor {

    @XNode("name")
    protected String name;

    @XNode("authenticationBaseUrl")
    protected String authenticationBaseUrl;

    @XNode("baseUrl")
    protected String baseUrl;

    @XNode("tokenGrantType")
    protected String tokenGrantType;

    @XNode("tokenScope")
    protected String tokenScope;

    @XNode("clientId")
    protected String clientId;

    @XNode("clientSecret")
    protected String clientSecret;

    @Override
    public String getId() {
        return name;
    }

    public String getName() {
        return name;
    }

    public String getAuthenticationBaseUrl() {
        return authenticationBaseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getTokenGrantType() {
        return tokenGrantType;
    }

    public String getTokenScope() {
        return tokenScope;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    /** @return {@code true} when the endpoints and the client credentials are all set */
    public boolean hasAllValues() {
        return StringUtils.isNoneBlank(authenticationBaseUrl, baseUrl, clientId, clientSecret);
    }
}
