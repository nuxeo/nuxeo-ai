/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.pipes.functions;

import static org.nuxeo.ai.pipes.services.JacksonUtil.toDoc;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toRecord;
import static org.nuxeo.ecm.core.api.AbstractSession.BINARY_TEXT_SYS_PROP;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.pipes.services.PipelineService;
import org.nuxeo.ai.pipes.types.BlobTextStream;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Listens to the "binaryTextUpdated" event and schedules an async post-commit worker to create
 * a BlobTextStream containing the binary text.
 * It uses a windowing approach to only emit an event if it hasn't been emitted for {timeout} seconds.
 * If the window size is less than zero then the work is scheduled anyway.
 */
public class BinaryTextListener implements EventListener {

    public static final String BINARY_TEXT_STREAM_KV = "BINARY_TEXT_STREAM";

    private static final Log log = LogFactory.getLog(BinaryTextListener.class);

    protected final String binaryProperty;

    protected final String consumerName;

    protected final int timeout;

    protected final boolean useWindow;

    public BinaryTextListener(String consumerName, String binaryProperty, int windowSizeSeconds) {
        this.binaryProperty = StringUtils.isNoneBlank(binaryProperty) ? binaryProperty : BINARY_TEXT_SYS_PROP;
        this.consumerName = consumerName;
        this.timeout = windowSizeSeconds;
        this.useWindow = timeout > 0;
    }

    @Override
    public void handleEvent(Event event) {
        DocumentModel doc = toDoc(event);
        Boolean hasText = (Boolean) event.getContext().getProperty(binaryProperty);

        if (doc != null && hasText != null && hasText) {

            if (useWindow) {
                KeyValueStore kvStore = Framework.getService(KeyValueService.class).getKeyValueStore(BINARY_TEXT_STREAM_KV);
                byte[] existing = kvStore.get(doc.getId());
                if (existing == null) {
                    scheduleWork(doc);
                    kvStore.put(doc.getId(), "", timeout);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Work IS ALREADY SCHEDULED for " + doc.getId());
                    }
                }
            } else {
                scheduleWork(doc);
            }

        }
    }

    /**
     * Schedules text work on this document.
     */
    protected void scheduleWork(DocumentModel doc) {
        if (log.isDebugEnabled()) {
            log.debug("Scheduling text to stream work for doc " + doc.getId());
        }
        Framework.getService(WorkManager.class)
                 .schedule(new TextToStreamWork(binaryProperty, consumerName, doc.getRepositoryName(), doc.getId()), true);
    }

    /**
     * Sends binary text to a Record consuming stream.
     */
    public static class TextToStreamWork extends AbstractWork {

        private static final Log log = LogFactory.getLog(TextToStreamWork.class);

        private static final long serialVersionUID = 164995918890660173L;

        protected final String binaryProperty;

        protected final String consumerName;

        public TextToStreamWork(String binaryProperty, String consumerName, String repositoryName, String docId) {
            super();
            this.binaryProperty = binaryProperty;
            this.consumerName = consumerName;
            setDocument(repositoryName, docId);
        }

        @Override
        public void work() {
            openSystemSession();

            IdRef docRef = new IdRef(docId);
            if (!session.exists(docRef)) {
                // doc is gone
                return;
            }
            DocumentModel doc = session.getDocument(docRef);
            Map<String, String> binText = doc.getBinaryFulltext();
            BlobTextStream blobTextStream = new BlobTextStream(doc);
            binText.values().forEach(text -> {
                if (StringUtils.isNotBlank(text)) {
                    blobTextStream.addProperty(binaryProperty, text);
                    if (log.isDebugEnabled()) {
                        log.debug("Writing record for " + blobTextStream.getId());
                    }
                    Consumer<Record> consumer = Framework.getService(PipelineService.class).getConsumer(consumerName);
                    if (consumer != null) {
                        consumer.accept(toRecord(blobTextStream.getKey(), blobTextStream));
                    }

                }
            });
        }

        @Override
        public String getTitle() {
            return "TextToStreamWork";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) { return true; }
            if (o == null || getClass() != o.getClass()) { return false; }
            if (!super.equals(o)) { return false; }
            TextToStreamWork that = (TextToStreamWork) o;
            return Objects.equals(binaryProperty, that.binaryProperty) &&
                    Objects.equals(consumerName, that.consumerName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), binaryProperty, consumerName);
        }
    }
}
