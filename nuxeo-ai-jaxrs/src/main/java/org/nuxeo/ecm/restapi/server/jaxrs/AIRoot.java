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
package org.nuxeo.ecm.restapi.server.jaxrs;

import static org.nuxeo.ai.listeners.ContinuousExportListener.NUXEO_AI_CONTINUOUS_EXPORT_ENABLE;

import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.services.AIConfigurationService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.webengine.model.Resource;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.DefaultObject;
import org.nuxeo.runtime.api.Framework;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Start endpoint for AI Core Config custom REST api.
 *
 * @since 2.4.1
 */
@WebObject(type = "aicore")
public class AIRoot extends DefaultObject {

    private static final Logger log = LogManager.getLogger(AIRoot.class);

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected static final TypeReference<Map<String, String>> NUXEO_CONF_REF = new TypeReference<Map<String, String>>() {
    };

    @POST
    @Path("config")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setNuxeoConfVars(String conf) throws JsonProcessingException {
        Map<String, String> confMap = MAPPER.readValue(conf, NUXEO_CONF_REF);
        if (!ctx.getPrincipal().isAdministrator()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            confMap.keySet().forEach((key) -> {
                Framework.getProperties().put(key, confMap.get(key));
                Framework.getService(AIConfigurationService.class).setConfVar(key, confMap.get(key));
            });
            return Response.status(Response.Status.OK).build();
        } catch (NuxeoException e) {
            log.error("Cannot set Nuxeo conf variables", e);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("config/export")
    @Produces(MediaType.TEXT_PLAIN)
    public Response isExportActivated() {
        String isActivated = (String) Framework.getProperties()
                                               .getOrDefault(NUXEO_AI_CONTINUOUS_EXPORT_ENABLE, "false");
        return Response.ok(isActivated).build();
    }

    @Path("extension")
    public Resource getExtension() {
        return ctx.newObject(ExtensionObject.TYPE);
    }
    
    @Path("metrics")
    public Resource getMetrics() {
        return ctx.newObject(MetricsObject.TYPE);
    }

}
