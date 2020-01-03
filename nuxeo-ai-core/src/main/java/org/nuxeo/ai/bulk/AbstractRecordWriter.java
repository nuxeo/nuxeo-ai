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

import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    public static final int DEFAULT_BLOB_TTL_SEC = 7200; // 2 hours

    public static final String BUFFER_SIZE_OPT = "bufferSize";

    public static final String BLOB_PROVIDER_OPT = "blobProvider";

    protected static final Logger log = LogManager.getLogger(AbstractRecordWriter.class);

    private static final String RECORD_STREAM_KV = "RECORD_STREAM_WRITER";

    protected final String name;

    protected int bufferSize;

    protected String blobProviderName;

    public AbstractRecordWriter(String name) {
        this.name = name;
    }

    public static String makeKey(String commandId, String name) {
        return commandId + "_" + name;
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
                Blob created = createBlob(file);
                String blobRef = provider.writeBlob(created);
                Blob managedBlob = getBlobFromProvider(provider, blobRef, created.getLength(), created.getMimeType());
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
            try {
                File tempFile = Framework.createTempFile(id, name);
                Framework.trackFile(tempFile, this);
                kvStore.put(key, tempFile.getAbsolutePath(), DEFAULT_BLOB_TTL_SEC);
                if (log.isDebugEnabled()) {
                    log.debug("Tmp record file {} created ", tempFile.getAbsolutePath());
                }
                return tempFile;
            } catch (IOException e) {
                throw new NuxeoException("Unable to create a temp file. ", e);
            }
        } else {
            return new File(existing);
        }

    }

}
