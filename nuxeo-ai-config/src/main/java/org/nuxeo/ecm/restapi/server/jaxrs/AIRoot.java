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

import java.io.IOException;
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
import org.nuxeo.ai.configuration.ThresholdConfiguratorDescriptor;
import org.nuxeo.ai.services.AIConfigurationService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.DefaultObject;
import org.nuxeo.runtime.RuntimeService;
import org.nuxeo.runtime.api.Framework;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Start endpoint for AI Core Config custom REST api.
 *
 * @since 2.4.1
 */
@WebObject(type = "ai")
public class AIRoot extends DefaultObject {

    private static final Logger log = LogManager.getLogger(AIRoot.class);

    protected RuntimeService runtimeService;

    protected AIConfigurationService aiConfigurationService;

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected static final TypeReference<Map<String, String>> NUXEOCONF = new TypeReference<Map<String, String>>() {
    };

    @Override
    protected void initialize(Object... args) {
        super.initialize(args);
        runtimeService = Framework.getRuntime();
        aiConfigurationService = Framework.getService(AIConfigurationService.class);
    }

    @POST
    @Path("config")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setNuxeoConfVars(String conf) throws JsonProcessingException {
        Map<String, String> confMap = MAPPER.readValue(conf, NUXEOCONF);
        if (!ctx.getPrincipal().isAdministrator()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            confMap.keySet().forEach(key -> {
                runtimeService.setProperty(key, confMap.get(key));
            });
            return Response.status(Response.Status.OK).build();
        } catch (NuxeoException e) {
            log.error("Cannot set Nuxeo conf variables", e);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @POST
    @Path("extension/threshold")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setThresholdsFromJSON(String thresholdsJSON) throws JsonProcessingException {
        ThresholdConfiguratorDescriptor thresholds = MAPPER.readValue(thresholdsJSON,
                ThresholdConfiguratorDescriptor.class);
        if (!ctx.getPrincipal().isAdministrator()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            aiConfigurationService.setThresholds(thresholds);
            return Response.status(Response.Status.OK).build();
        } catch (IOException | NuxeoException e) {
            log.error("Cannot set thresholds", e);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @POST
    @Path("extension/thresholds")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setThresholdsFromXML(String thresholdsXML) {
        if (!ctx.getPrincipal().isAdministrator()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            aiConfigurationService.setThresholds(thresholdsXML);
            return Response.status(Response.Status.OK).build();
        } catch (NuxeoException e) {
            log.error("Cannot set thresholds", e);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("extension/thresholds")
    @Produces(MediaType.APPLICATION_XML)
    public Response getAllThresholds() {
        if (!ctx.getPrincipal().isAdministrator()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            String thresholds = aiConfigurationService.getAllThresholdsXML();
            return Response.ok(thresholds).build();
        } catch (NuxeoException | IOException e) {
            log.error("Cannot get all thresholds", e);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
