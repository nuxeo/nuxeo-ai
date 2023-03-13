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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.io.IOUtils;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.nuxeo.ecm.core.api.CloseableCoreSession;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.AbstractResource;
import org.nuxeo.ecm.webengine.model.impl.ResourceTypeImpl;
import org.nuxeo.elasticsearch.api.ESClient;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.elasticsearch.client.ESRestClient;
import org.nuxeo.elasticsearch.http.readonly.filter.DefaultSearchRequestFilter;
import org.nuxeo.elasticsearch.http.readonly.filter.SearchRequestFilter;
import org.nuxeo.elasticsearch.http.readonly.service.RequestFilterService;
import org.nuxeo.runtime.api.Framework;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.opensearch.client.Request;
import org.opensearch.client.Response;

/**
 * Wrapping Nuxeo search web object to be able to introspect index names
 */
@WebObject(type = AISearchObject.TYPE)
@Produces(MediaType.APPLICATION_JSON)
public class AISearchObject extends AbstractResource<ResourceTypeImpl> {

    protected static final Logger log = LogManager.getLogger(AISearchObject.class);

    protected static final String TYPE = "ai.search";

    protected static final String AUDIT = "audit";

    public static final String TEMPLATE_FILE_NAME = "/es-query-template.json.ftl";

    public static final String MODEL_NAME = "modelName";

    public static final String EVENT_IDS = "eventIds";

    public static final String FROM = "from";

    public static final String TO = "to";

    public static final String VALUE = "value";

    public static final String AGG = "agg";

    public static final String DEFAULT_EVENT_IDS = "\"AUTO_FILLED\",\"AUTO_CORRECTED\"";

    public static final String AUTO_FILLED = "AUTO_FILLED";

    public static final String AUTO_CORRECTED = "AUTO_CORRECTED";

    protected String esBaseUrl;

    protected Configuration cfg;

    @Override
    protected void initialize(Object... args) {
        super.initialize(args);
        cfg = initFreeMarker();
    }

    @GET
    @Path("models")
    @Produces(MediaType.APPLICATION_JSON)
    public String modelsESearch(@Context UriInfo uriInf, @QueryParam(MODEL_NAME) String modelName,
            @QueryParam(EVENT_IDS) String eventIds, @QueryParam(FROM) String from, @QueryParam(TO) String to,
            @QueryParam(VALUE) String value, @QueryParam(AGG) boolean agg) throws IOException, TemplateException {
        try (CloseableCoreSession session = openCoreSessionSystem(getContext().getCoreSession().getRepositoryName(),
                getContext().getPrincipal().getName())) {
            String payload = getESQuery(modelName, eventIds, from, to, value, agg);
            return doSearchWithPayload(session, payload);
        }
    }

    public String getESQuery(String modelName, String eventIds, String from, String to, String value, boolean agg)
            throws IOException, TemplateException {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(MODEL_NAME, modelName);
        eventIds = filterEventIds(eventIds);
        inputs.put(EVENT_IDS, eventIds);
        inputs.put(FROM, from);
        inputs.put(TO, to);
        if (value != null) {
            inputs.put(VALUE, Integer.valueOf(value));
        }
        if (agg) {
            inputs.put(AGG, true);
        }
        if (cfg == null) {
            cfg = initFreeMarker();
        }
        Template template = cfg.getTemplate(AUDIT);
        StringWriter out = new StringWriter();
        template.process(inputs, out);
        return out.toString();
    }

    @NotNull
    protected String filterEventIds(String eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return DEFAULT_EVENT_IDS;
        }
        // Here we are ensuring the user cannot see more from the audit
        int eventsNb = eventIds.split(",").length;
        if (eventsNb >= 2) {
            return DEFAULT_EVENT_IDS;
        }
        if (eventIds.contains(AUTO_FILLED) || eventIds.contains(AUTO_CORRECTED)) {
            return eventIds;
        }
        return DEFAULT_EVENT_IDS;
    }

    protected String doSearchWithPayload(CoreSession session, String payload) {
        RequestFilterService requestFilterService = Framework.getService(RequestFilterService.class);
        try {
            SearchRequestFilter filter = requestFilterService.getRequestFilters(AUDIT);
            if (filter == null) {
                filter = new DefaultSearchRequestFilter();
            }
            filter.init(session, AUDIT, "", payload);
            log.debug(filter);

            ESClient esClient = Framework.getService(ElasticSearchAdmin.class).getClient();
            if (!(esClient instanceof ESRestClient)) {
                throw new IllegalStateException("Passthrough works only with a RestClient");
            }
            ESRestClient client = (ESRestClient) esClient;
            Request request = new Request("GET", filter.getUrl());
            if (payload != null) {
                request.setJsonEntity(payload);
            }
            Response response = client.performRequestWithTracing(request);
            return EntityUtils.toString(response.getEntity());
        } catch (Exception e) {
            log.error("Error when trying to get Search Request Filter for index audit", e);
            return null;
        }
    }

    protected Configuration initFreeMarker() {
        try (InputStream stream = this.getClass().getResourceAsStream(TEMPLATE_FILE_NAME)) {
            String content = IOUtils.toString(stream, StandardCharsets.UTF_8);
            Configuration cfg = new Configuration(Configuration.VERSION_2_3_0);
            cfg.setClassForTemplateLoading(AISearchObject.class, "org.nuxeo.ai");
            cfg.setDefaultEncoding("UTF-8");
            cfg.setLocale(Locale.US);
            StringTemplateLoader stringLoader = new StringTemplateLoader();
            stringLoader.putTemplate(AUDIT, content);
            cfg.setTemplateLoader(stringLoader);
            cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
            return cfg;
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }
}
