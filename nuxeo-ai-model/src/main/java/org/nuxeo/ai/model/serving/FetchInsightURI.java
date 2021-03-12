/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo
 */
package org.nuxeo.ai.model.serving;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.nuxeo.ai.auth.NuxeoClaim;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ai.cloud.CloudConfigDescriptor;
import org.nuxeo.ai.keystore.JWTKeyService;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.runtime.api.Framework;

import com.auth0.jwt.impl.PublicClaims;

/**
 * Fetching Insight URI for the current Nuxeo instance
 */
@Operation(id = FetchInsightURI.ID, category = Constants.CAT_DOCUMENT, label = "Fetching Insight URI", description = "Fetching Insight URI for the current Nuxeo instance")
public class FetchInsightURI {

    public static final String ID = "AI.FetchInsightURI";

    @Context
    protected CoreSession session;

    @OperationMethod
    public Blob run() throws IOException {
        CloudClient cloudClient = Framework.getService(CloudClient.class);
        CloudConfigDescriptor cloudConfig = cloudClient.getCloudConfig();
        if (cloudConfig == null) {
            // Trick to return a 404 http code response (RestOperationContext doesn't work anymore)
            throw new DocumentNotFoundException("Client Cloud configuration is missing");
        }
        String projectId = cloudConfig.getProjectId();
        String url = cloudConfig.getUrl();
        JWTKeyService jwt = Framework.getService(JWTKeyService.class);
        Map<String, Serializable> claims = new HashMap<>();
        claims.put(PublicClaims.SUBJECT, session.getPrincipal().getActingUser());
        String[] groups = session.getPrincipal()
                                 .getAllGroups()
                                 .stream()
                                 .filter(group -> group.startsWith(projectId))
                                 .toArray(String[]::new);
        if (groups.length > 0) {
            claims.put(NuxeoClaim.GROUP, groups);
        }
        String token = jwt.generateJWT(projectId, claims);
        String urlCore = Framework.getProperty("nuxeo.url");
        return buildResponse(url, token, projectId, urlCore);
    }

    protected Blob buildResponse(String url, String token, String projectId, String urlCore) throws IOException {
        Map<String, String> response = new HashMap<>();
        response.put("url", String.format("%s/ai/#!/", url));
        response.put("urlCore", urlCore);
        response.put("aitoken", token);
        response.put("projectId", projectId);
        return Blobs.createJSONBlobFromValue(response);
    }
}
