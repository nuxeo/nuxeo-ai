/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.endpoint;

import static java.util.Collections.singletonMap;
import static org.nuxeo.ai.rekognition.listeners.AsyncLabelResultListener.JOB_ID_CTX_KEY;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.enrichment.async.DetectCelebritiesEnrichmentService;
import org.nuxeo.ai.enrichment.async.DetectFacesEnrichmentService;
import org.nuxeo.ai.enrichment.async.DetectUnsafeImagesEnrichmentService;
import org.nuxeo.ai.enrichment.async.LabelsEnrichmentService;
import org.nuxeo.ai.rekognition.listeners.AsyncCelebritiesResultListener;
import org.nuxeo.ai.rekognition.listeners.AsyncFaceResultListener;
import org.nuxeo.ai.rekognition.listeners.AsyncLabelResultListener;
import org.nuxeo.ai.rekognition.listeners.AsyncUnsafeResultListener;
import org.nuxeo.ai.sns.Notification;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.runtime.api.Framework;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Endpoints responsible for listening notifications from external services such as AWS SNS
 */
@Path("/ai/rekognition")
@WebObject(type = "ai")
public class Rekognition {

    private static final Logger log = LogManager.getLogger(Rekognition.class);

    private static final String SUBSCRIPTION_CONFIRMATION = "SubscriptionConfirmation";

    private static final String TYPE_JSON_FIELD= "Type";

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Generic Endpoint responsible for delegating tasks among enrichment services
     * Runs as an asynchronous dispatcher sending success/failure events to the following services:
     * - {@link LabelsEnrichmentService} for label processing
     * - {@link DetectFacesEnrichmentService} for faces tracking
     * - {@link DetectCelebritiesEnrichmentService} for celebrity detection
     * - {@link DetectUnsafeImagesEnrichmentService} for unsafe content detection
     * @param request of a Notification service
     * @return {@link Response}
     */
    @POST
    @Path("callback/labels")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    public Response callback(@Context HttpServletRequest request) throws IOException {
        Notification.Message message;
        String json = null;
        try {
            json = IOUtils.toString(request.getInputStream(), Charset.defaultCharset());
            Notification notification = OBJECT_MAPPER.readValue(json, Notification.class);
            message = OBJECT_MAPPER.readValue(notification.message(), Notification.Message.class);
        } catch (IOException e) {
            if (tryConfirmation(json)) {
                return Response.ok().build();
            }

            log.error("Could not get Notification from service request");
            return Response.serverError().build();
        }

        log.debug("Received notification from Rekognition {}", message.getApi());

        EventContextImpl ctx = new EventContextImpl();
        Map<String, Serializable> props = singletonMap(JOB_ID_CTX_KEY, message.getJobId());
        ctx.setProperties(props);

        String event;
        boolean succeeded = message.isSucceeded();
        switch (message.getApi()) {
            case LabelsEnrichmentService
                    .ASYNC_ACTION_NAME:
                event = succeeded ? AsyncLabelResultListener.SUCCESS_EVENT
                        : AsyncLabelResultListener.FAILURE_EVENT;
                break;
            case DetectFacesEnrichmentService
                    .ASYNC_ACTION_NAME:
                event = succeeded ? AsyncFaceResultListener.SUCCESS_EVENT
                        : AsyncFaceResultListener.FAILURE_EVENT;
                break;
            case DetectCelebritiesEnrichmentService
                    .ASYNC_ACTION_NAME:
                event = succeeded ? AsyncCelebritiesResultListener.SUCCESS_EVENT
                        : AsyncCelebritiesResultListener.FAILURE_EVENT;
                break;
            case DetectUnsafeImagesEnrichmentService
                    .ASYNC_ACTION_NAME:
                event = succeeded ? AsyncUnsafeResultListener.SUCCESS_EVENT
                        : AsyncUnsafeResultListener.FAILURE_EVENT;
                break;
            default:
                throw new NuxeoException("Unknown API used: " + message.getApi());
        }

        EventService es = Framework.getService(EventService.class);
        es.fireEvent(event, ctx);

        return Response.ok().build();
    }

    protected boolean tryConfirmation(String json) throws IOException {
        if (json != null) {
            log.debug("Could not read Notification, trying SNS Confirmation");
            @SuppressWarnings("unchecked")
            Map<String, Serializable> confirmation = OBJECT_MAPPER.readValue(json, Map.class);
            String type = (String) confirmation.get(TYPE_JSON_FIELD);
            if (SUBSCRIPTION_CONFIRMATION.equals(type)) {
                String subscribeURL = (String) confirmation.get("SubscribeURL");
                if (StringUtils.isNotBlank(subscribeURL)) {
                    URL url = new URL(subscribeURL);
                    try (InputStream is = url.openConnection().getInputStream()) {
                        log.debug("Confirming SNS subscription");
                        /* NOP */
                    }
                }
                return true;
            }
        }
        return false;
    }
}
