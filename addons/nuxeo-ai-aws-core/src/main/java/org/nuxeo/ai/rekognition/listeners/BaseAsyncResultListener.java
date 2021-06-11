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
package org.nuxeo.ai.rekognition.listeners;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.services.DocMetadataService;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.validation.DocumentValidationException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;
import org.nuxeo.runtime.transaction.TransactionHelper;
import com.amazonaws.services.rekognition.model.AmazonRekognitionException;

/**
 * Base class for listening notifications on detect results
 */
public abstract class BaseAsyncResultListener implements PostCommitEventListener {

    private static final Logger log = LogManager.getLogger(BaseAsyncResultListener.class);

    public static final String JOB_ID_CTX_KEY = "jobId";

    @Override
    public void handleEvent(EventBundle eb) {
        eb.forEach(this::handleEvent);
    }

    protected void handleEvent(Event event) {
        EventContext ctx = event.getContext();
        if (ctx == null) {
            return;
        }

        String jobId = (String) ctx.getProperties().get(JOB_ID_CTX_KEY);
        log.debug("Received result for {}; status: {}", jobId, event.getName());
        if (getFailureEventName().equals(event.getName())) {
            log.info("Async Rekognition job failed for id " + jobId);
            return;
        }

        byte[] bytes = getStore().get(jobId);
        if (bytes == null) {
            log.error("Could not find data for JobId " + jobId);
            return;
        }
        getStore().setTTL(jobId, 1); // release key

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                ObjectInputStream ois = new ObjectInputStream(bais)) {

            @SuppressWarnings("unchecked")
            Map<String, Serializable> params = (Map<String, Serializable>) ois.readObject();

            Collection<EnrichmentMetadata> enrichment;
            enrichment = getEnrichmentMetadata(jobId, params);
            enrichment.forEach(this::saveMetadata);
        } catch (IOException | ClassNotFoundException e) {
            log.error("An error occurred during event process {}, for event {}", e.getMessage(), event.getName());
        } catch (AmazonRekognitionException e) {
            log.error("An error occurred at AWS Rekognition {}, for event {}", e.getMessage(), event.getName());
        }
    }

    protected void saveMetadata(EnrichmentMetadata metadata) {
        TransactionHelper.runInTransaction(() -> CoreInstance.doPrivileged(metadata.context.repositoryName, session -> {
            DocMetadataService docMetadataService = Framework.getService(DocMetadataService.class);
            DocumentModel doc = docMetadataService.saveEnrichment(session, metadata);
            if (doc != null) {
                try {
                    session.saveDocument(doc);
                } catch (DocumentValidationException e) {
                    log.warn("Failed to save document enrichment data for {}.", metadata.context.documentRef, e);
                }
            } else {
                log.debug("Failed to save enrichment for document {}.", metadata.context.documentRef);
            }
        }));
    }

    protected KeyValueStore getStore() {
        KeyValueService kvs = Framework.getService(KeyValueService.class);
        return kvs.getKeyValueStore("default");
    }

    /**
     * @return {@link String} name of successful event
     */
    protected abstract String getSuccessEventName();

    /**
     * @return {@link String} name of failure event
     */
    protected abstract String getFailureEventName();

    /**
     * @param jobId  reference for AWS Rekognition Job
     * @param params additional parameters
     * @return A {@link Collection} of {@link EnrichmentMetadata} for given job id
     */
    protected abstract Collection<EnrichmentMetadata> getEnrichmentMetadata(String jobId,
            Map<String, Serializable> params);

}
