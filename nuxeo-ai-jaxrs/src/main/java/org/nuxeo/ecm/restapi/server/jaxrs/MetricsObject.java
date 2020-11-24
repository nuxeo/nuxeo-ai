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
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.auto.AutoService;
import org.nuxeo.ai.auto.AutoServiceImpl;
import org.nuxeo.ai.configuration.ThresholdConfiguratorDescriptor;
import org.nuxeo.ai.services.AIConfigurationService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.AbstractResource;
import org.nuxeo.ecm.webengine.model.impl.ResourceTypeImpl;
import org.nuxeo.runtime.api.Framework;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Metrics endpoints.
 *
 * @since 2.5.0
 */
@WebObject(type = MetricsObject.TYPE)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MetricsObject extends AbstractResource<ResourceTypeImpl> {

    private static final Logger log = LogManager.getLogger(MetricsObject.class);

    public static final String TYPE = "aicore.metrics";

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    @GET
    @Path("global")
    public Response getGlobalMetrics() throws JsonProcessingException {
        AutoService autoService = Framework.getService(AutoService.class);
        Map<String, AutoServiceImpl.Metrics> metrics = autoService.getGlobalMetrics();
        return Response.ok(MAPPER.writeValueAsString(metrics)).build();
    }

    @GET
    @Path("timeseries")
    public Response getTimeSeriesMetrics() throws JsonProcessingException {
        AutoService autoService = Framework.getService(AutoService.class);
        Map<String, AutoServiceImpl.Metrics> metrics = autoService.getGlobalMetrics();
        return Response.ok(MAPPER.writeValueAsString(metrics)).build();
    }

}
