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
package org.nuxeo.ecm.restapi.server;

import static org.nuxeo.ecm.core.api.CoreInstance.getCoreSessionSystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.nuxeo.ai.services.SearchAdapterService;
import org.nuxeo.ai.services.SearchOptions;
import org.nuxeo.ai.services.SearchSummary;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.AbstractResource;
import org.nuxeo.ecm.webengine.model.impl.ResourceTypeImpl;
import org.nuxeo.runtime.api.Framework;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

/**
 * Wrapping Nuxeo search web object to be able to introspect index names
 */
@WebObject(type = AISearchObject.TYPE)
@Produces(MediaType.APPLICATION_JSON)
public class AISearchObject extends AbstractResource<ResourceTypeImpl> {

    protected static final Logger log = LogManager.getLogger(AISearchObject.class);

    protected static final String TYPE = "ai.search";

    protected static final String DEFAULT_ES_BASE_URL = "http://localhost:9200/";

    protected static final String AUDIT = "audit";

    public static final String ALL = "_all";

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
        CoreSession session = getCoreSessionSystem(getContext().getCoreSession().getRepositoryName(),
                getContext().getPrincipal().getName());
        String payload = getESQuery(modelName, eventIds, from, to, value, agg);
        return doSearchWithPayload(session, payload);
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
        try {
            SearchAdapterService adapter = Framework.getService(SearchAdapterService.class);
            String nxqlQuery = "SELECT * FROM LogEntry ORDER BY eventDate DESC";
            SearchSummary summary = adapter.search(session, nxqlQuery,
                    SearchOptions.builder().index(SearchAdapterService.DEFAULT_INDEX).limit(100).build());
            return String.format("{\"total\": %d, \"hits\": %d}", summary.getTotal(), summary.getHitsCount());
        } catch (Exception e) {
            log.error("Error when trying to execute search request on audit index", e);
            return null;
        }
    }

    protected Configuration initFreeMarker() {
        try (InputStream stream = this.getClass().getResourceAsStream(TEMPLATE_FILE_NAME)) {
            String content = IOUtils.toString(stream, StandardCharsets.UTF_8);
            Configuration config = new Configuration(Configuration.VERSION_2_3_0);
            config.setClassForTemplateLoading(AISearchObject.class, "org.nuxeo.ai");
            config.setDefaultEncoding("UTF-8");
            config.setLocale(Locale.US);
            StringTemplateLoader stringLoader = new StringTemplateLoader();
            stringLoader.putTemplate(AUDIT, content);
            config.setTemplateLoader(stringLoader);
            config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
            return config;
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }
}
