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

import static org.nuxeo.ai.AIConstants.INSIGHT_PREFIX;
import static org.nuxeo.ai.AIConstants.LIBRARIANS_GROUP_SUFFIX;
import static org.nuxeo.ai.AIConstants.MANAGERS_GROUP_SUFFIX;
import static org.nuxeo.ai.listeners.ContinuousExportListener.NUXEO_AI_CONTINUOUS_EXPORT_ENABLE;

import java.io.IOException;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.configuration.ThresholdConfiguratorDescriptor;
import org.nuxeo.ai.model.serving.ModelServingService;
import org.nuxeo.ai.services.AIConfigurationService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.webengine.model.Resource;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.DefaultObject;
import org.nuxeo.runtime.api.Framework;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Start endpoint for AI Core Config custom REST api.
 *
 * @since 2.4.1
 */
@WebObject(type = "aicore")
public class AIRoot extends DefaultObject {

    private static final Logger log = LogManager.getLogger(AIRoot.class);

    protected static final ObjectMapper MAPPER;

    protected static final TypeReference<Map<String, String>> NUXEO_CONF_REF = new TypeReference<Map<String, String>>() {
    };

    public static final String DATASOURCE_CONF_VAR = "nuxeo.ai.insight.datasource.label";

    static {
        MAPPER = new ObjectMapper();
        MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    @POST
    @Path("config")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setNuxeoConfVars(String conf) throws IOException {
        Map<String, String> confMap = MAPPER.readValue(conf, NUXEO_CONF_REF);
        if (!(isManager() || isAdministrator())) {
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
    @Path("datasource")
    public String getDatasource() {
        return Framework.getProperty(DATASOURCE_CONF_VAR);
    }

    @GET
    @Path("config/export")
    @Produces(MediaType.TEXT_PLAIN)
    public Response isExportActivated() {
        String isActivated = (String) Framework.getProperties()
                                               .getOrDefault(NUXEO_AI_CONTINUOUS_EXPORT_ENABLE, "false");
        return Response.ok(isActivated).build();
    }

    @POST
    @Path("extension/threshold")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setThresholdsFromJSON(String thresholdsJSON) throws IOException {
        ThresholdConfiguratorDescriptor thresholds = MAPPER.readValue(thresholdsJSON,
                ThresholdConfiguratorDescriptor.class);
        if (!(isManager() || isAdministrator())) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        try {
            Framework.getService(AIConfigurationService.class).set(thresholds);
            return Response.status(Response.Status.OK).build();
        } catch (IOException | NuxeoException e) {
            log.error("Cannot set thresholds", e);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @POST
    @Path("extension/thresholds/{docType}")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setThresholdsFromXML(@PathParam("docType") String docType, String thresholdsXML) {
        if (docType == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST).entity("docType parameter is mandatory").build());
        }
        if (!(isManager() || isAdministrator())) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            Framework.getService(AIConfigurationService.class).set(docType, thresholdsXML);
            return Response.status(Response.Status.OK).build();
        } catch (NuxeoException e) {
            log.error("Cannot set thresholds", e);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @DELETE
    @Path("extension/thresholds/{docType}")
    public Response removeThreshold(@PathParam("docType") String docType) {
        if (docType == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST).entity("docType parameter is mandatory").build());
        }
        if (!(isManager() || isAdministrator())) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            Framework.getService(AIConfigurationService.class).remove(docType);
            return Response.status(Response.Status.NO_CONTENT).build();
        } catch (NuxeoException e) {
            log.error("Cannot set thresholds", e);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("extension/thresholds")
    @Produces(MediaType.APPLICATION_XML)
    public Response getAllThresholds() {
        if (!(isManager() || isAdministrator())) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            String thresholds = Framework.getService(AIConfigurationService.class)
                                         .getXML("thresholds", ThresholdConfiguratorDescriptor.class);
            return Response.ok(thresholds).build();
        } catch (NuxeoException | IOException e) {
            log.error("Cannot get all thresholds", e);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @POST
    @Path("extension/model/{modelId}")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setModelFromXML(@PathParam("modelId") String modelId, String modelXML) {
        if (!(isManager() || isAdministrator())) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            Framework.getService(AIConfigurationService.class).set(modelId, modelXML);
            return Response.status(Response.Status.OK).build();
        } catch (NuxeoException e) {
            log.error("Cannot set models", e);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("extension/models")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllModels() {
        if (!isAdministrator()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        try {
            ModelServingService mss = Framework.getService(ModelServingService.class);
            return Response.ok(MAPPER.writeValueAsString(mss.listModels())).build();
        } catch (NuxeoException | IOException e) {
            log.error("Cannot get models", e);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @DELETE
    @Path("extension/model/{modelId}")
    public Response deleteModel(@PathParam("modelId") String modelId) {
        if (modelId == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST).entity("modelId parameter is mandatory").build());
        }
        if (!(isManager() || isAdministrator())) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        try {
            Framework.getService(AIConfigurationService.class).remove(modelId);
            return Response.noContent().build();
        } catch (NuxeoException e) {
            log.error("Cannot get all models", e);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    /**
     * This api is retrieving document ids to annotate.
     *
     * @return document ids to annotate
     */
    @Path("search")
    public Resource getDocumentsToAnnotate() {
        if (!(isManager() || isLibrarian() || isAdministrator())) {
            return null;
        }
        return ctx.newObject(AISearchObject.TYPE);
    }

    private boolean isAdministrator() {
        return ctx.getPrincipal().isAdministrator();
    }

    private boolean isLibrarian() {
        return ctx.getPrincipal().isMemberOf(INSIGHT_PREFIX + LIBRARIANS_GROUP_SUFFIX);
    }

    private boolean isManager() {
        return ctx.getPrincipal().isMemberOf(INSIGHT_PREFIX + MANAGERS_GROUP_SUFFIX);
    }
}
