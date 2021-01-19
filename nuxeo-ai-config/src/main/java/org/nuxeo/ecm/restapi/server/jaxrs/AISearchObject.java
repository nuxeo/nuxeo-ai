/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.restapi.server.jaxrs;

import static org.nuxeo.ecm.core.api.CoreInstance.openCoreSessionSystem;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.CloseableCoreSession;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.AbstractResource;
import org.nuxeo.ecm.webengine.model.impl.ResourceTypeImpl;
import org.nuxeo.elasticsearch.http.readonly.HttpClient;
import org.nuxeo.elasticsearch.http.readonly.filter.DefaultSearchRequestFilter;
import org.nuxeo.elasticsearch.http.readonly.filter.SearchRequestFilter;
import org.nuxeo.elasticsearch.http.readonly.service.RequestFilterService;
import org.nuxeo.runtime.api.Framework;

/**
 * Wrapping Nuxeo search web object to be able to introspect index names
 */
@WebObject(type = AISearchObject.TYPE)
@Produces(MediaType.APPLICATION_JSON)
public class AISearchObject extends AbstractResource<ResourceTypeImpl> {

    protected static final Logger log = LogManager.getLogger(AISearchObject.class);

    public static final String TYPE = "ai.search";

    protected static final String DEFAULT_ES_BASE_URL = "http://localhost:9200/";

    protected static final java.lang.String ES_BASE_URL_PROPERTY = "elasticsearch.httpReadOnly.baseUrl";

    protected String esBaseUrl;

    @POST
    @Path("audit")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String auditSearch(@Context UriInfo uriInf, String payload) {
        try (CloseableCoreSession session = openCoreSessionSystem(getContext().getCoreSession().getRepositoryName(),
                getContext().getPrincipal().getName())) {
            return doSearchWithPayload(session, uriInf.getRequestUri().getRawQuery(), payload);
        }
    }

    protected String doSearchWithPayload(CoreSession session, String rawQuery, String payload) {
        RequestFilterService requestFilterService = Framework.getService(RequestFilterService.class);
        try {
            SearchRequestFilter req = requestFilterService.getRequestFilters("audit");
            if (req == null) {
                req = new DefaultSearchRequestFilter();
            }
            req.init(session, "audit", "_all", rawQuery, payload);
            log.debug(req);
            return HttpClient.get(getElasticsearchBaseUrl() + req.getUrl(), req.getPayload());
        } catch (Exception e) {
            log.error("Error when trying to get Search Request Filter for indice " + "audit", e);
            return null;
        }
    }

    protected String getElasticsearchBaseUrl() {
        if (esBaseUrl == null) {
            esBaseUrl = Framework.getProperty(ES_BASE_URL_PROPERTY, DEFAULT_ES_BASE_URL);
        }
        return esBaseUrl;
    }
}
