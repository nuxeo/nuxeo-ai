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

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toDoc;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toRecord;
import static org.nuxeo.ecm.core.api.AbstractSession.BINARY_TEXT_SYS_PROP;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.SYSTEM_PROPERTY_VALUE;

import java.util.function.Consumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.pipes.services.PipelineService;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;

/**
 * Listens to the "binaryTextUpdated" event and adds a BlobTextFromDocument containing the binary text to a stream.
 * It uses a windowing approach to only emit an event if it hasn't been emitted for {timeout} seconds.
 * If the window size is less than zero then writing to the stream is immediate.
 * <p>
 * The {binaryProperty} is derived from the {inputProperty}.  The input property will be checked for modifications
 * when removing metadata.
 */
public class BinaryTextListener implements EventListener {

    public static final String BINARY_TEXT_STREAM_KV = "BINARY_TEXT_STREAM";

    private static final Log log = LogFactory.getLog(BinaryTextListener.class);

    protected final String binaryProperty;

    protected final String inputProperty;

    protected final String consumerName;

    protected final int timeout;

    protected final boolean useWindow;

    public BinaryTextListener(String consumerName, String binaryProperty, String inputProperty, int windowSizeSeconds) {
        this.binaryProperty = isNotBlank(binaryProperty) ? binaryProperty : BINARY_TEXT_SYS_PROP;
        this.inputProperty = isNotBlank(inputProperty) ? inputProperty : NXQL.ECM_FULLTEXT;
        this.consumerName = consumerName;
        this.timeout = windowSizeSeconds;
        this.useWindow = timeout > 0;
        if (log.isDebugEnabled()) {
            log.debug(String.format("Creating a BinaryTextListener for %s property and a %s source property.  " +
                                            "Sending to a '%s' stream, window size is %s (in seconds)",
                                    binaryProperty, inputProperty, consumerName, windowSizeSeconds));
        }
    }

    @Override
    public void handleEvent(Event event) {
        DocumentModel doc = toDoc(event);
        Boolean hasText = (Boolean) event.getContext().getProperty(binaryProperty);

        if (doc != null && hasText != null && hasText) {
            String text = (String) event.getContext().getProperty(SYSTEM_PROPERTY_VALUE);
            if (isNotBlank(text)) {
                if (useWindow) {
                    KeyValueStore kvStore = Framework.getService(KeyValueService.class)
                                                     .getKeyValueStore(BINARY_TEXT_STREAM_KV);
                    byte[] existing = kvStore.get(doc.getId());
                    if (existing == null) {
                        if (handledBinaryText(doc, text)) {
                            kvStore.put(doc.getId(), "", timeout);
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug(String.format("Skipping because there is already an event for %s and %s",
                                                    doc.getId(),
                                                    text.substring(0, text.length() < 25 ? text.length() : 25)));
                        }
                    }
                } else {
                    handledBinaryText(doc, text);
                }
            }

        }
    }

    /**
     * Handles the binary text
     */
    protected boolean handledBinaryText(DocumentModel doc, String text) {
        Consumer<Record> consumer = Framework.getService(PipelineService.class).getConsumer(consumerName);
        if (consumer != null) {
            if (log.isDebugEnabled()) {
                log.debug("Handling new binary text for doc " + doc.getId());
            }
            BlobTextFromDocument blobTextFromDoc = new BlobTextFromDocument(doc);
            blobTextFromDoc.addProperty(inputProperty, text);
            consumer.accept(toRecord(blobTextFromDoc.getKey(), blobTextFromDoc));
            return true;
        }
        return false;
    }

}
