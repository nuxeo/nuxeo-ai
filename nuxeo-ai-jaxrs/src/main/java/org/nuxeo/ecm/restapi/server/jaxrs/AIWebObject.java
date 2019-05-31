/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.restapi.server.jaxrs;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.DefaultObject;
import org.nuxeo.runtime.api.Framework;

/**
 * Contribute a new WebObject to create /api/v1/ai path.
 */
@WebObject(type = "ai")
@Produces(MediaType.APPLICATION_JSON)
public class AIWebObject extends DefaultObject {

    protected static Logger log = LogManager.getLogger(AIWebObject.class);

    /**
     * GET from the cloud by a project path
     */
    @GET
    @Path("/cloud/project/{pathSuffix:.*}")
    public Response getByProject(@PathParam("pathSuffix") String path) {

        Response toReturn = Framework.getService(CloudClient.class).getByProject("/" + path, response -> {
            if (response.isSuccessful()) {
                return Response.ok(response.body().string()).build();
            } else {
                log.warn("Unsuccessful call to cloud: " + response.toString());
                return Response.status(response.code()).build();
            }
        });
        return toReturn == null ? Response.status(Response.Status.BAD_REQUEST).build() : toReturn;
    }

}
