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
package org.nuxeo.ai.bulk;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.getBlobFromProvider;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.optionAsInteger;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.pipes.streams.Initializable;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;

/**
 * Writes Record to a File
 */
public abstract class AbstractRecordWriter implements RecordWriter, Initializable {

    public static final int DEFAULT_BUFFER_SIZE = 524288;  //512K

    public static final String BUFFER_SIZE_OPT = "bufferSize";

    public static final String BLOB_PROVIDER_OPT = "blobProvider";

    protected static final Log log = LogFactory.getLog(AbstractRecordWriter.class);

    private static final String RECORD_STREAM_KV = "RECORD_STREAM_WRITER";

    protected final String name;

    protected int bufferSize;

    protected String blobProviderName;

    public AbstractRecordWriter(String name) {
        this.name = name;
    }

    public static String makeKey(String commandId, String name) {
        return commandId + name;
    }

    @Override
    public void init(Map<String, String> options) {
        this.bufferSize = optionAsInteger(options, BUFFER_SIZE_OPT, DEFAULT_BUFFER_SIZE);
        this.blobProviderName = options.get(BLOB_PROVIDER_OPT);
    }

    @Override
    public Optional<Blob> complete(String id) throws IOException {
        KeyValueStore kvStore = Framework.getService(KeyValueService.class).getKeyValueStore(RECORD_STREAM_KV);
        String filename = kvStore.getString(makeKey(id, name));
        if (filename != null && isNotBlank(blobProviderName)) {
            File file = new File(filename);
            if (file.exists() && file.length() > 0) {
                BlobProvider provider = Framework.getService(BlobManager.class).getBlobProvider(blobProviderName);
                String blobRef = provider.writeBlob(createBlob(file));
                Blob managedBlob = getBlobFromProvider(provider, blobRef);
                if (managedBlob != null) {
                    return Optional.of(managedBlob);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Create a blob using the File, can be overridden by subclasses.
     */
    protected Blob createBlob(File file) throws IOException {
        return Blobs.createBlob(file);
    }

    @Override
    public boolean exists(String id) {
        KeyValueStore kvStore = Framework.getService(KeyValueService.class).getKeyValueStore(RECORD_STREAM_KV);
        return kvStore.getString(makeKey(id, name)) != null;
    }

    public String getBlobProviderName() {
        return blobProviderName;
    }

    /**
     * Gets a reference to a File for this id.  If the id is found then an existing File is returned, otherwise
     * a new temp file is created.
     */
    protected File getFile(String id) {
        KeyValueStore kvStore = Framework.getService(KeyValueService.class).getKeyValueStore(RECORD_STREAM_KV);
        String key = makeKey(id, name);
        String existing = kvStore.getString(key);
        if (existing == null) {
            if (log.isDebugEnabled()) {
                log.debug("Creating a new file to write to for " + key);
            }
            try {
                File tempFile = Framework.createTempFile(id, name);
                kvStore.put(key, tempFile.getAbsolutePath());
                if (log.isDebugEnabled()) {
                    log.debug("New file is " + tempFile.getAbsolutePath());
                }
                return tempFile;
            } catch (IOException e) {
                throw new NuxeoException("Unable to create a temp file. ", e);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Existing file for " + key);
            }
            return new File(existing);
        }

    }

}
