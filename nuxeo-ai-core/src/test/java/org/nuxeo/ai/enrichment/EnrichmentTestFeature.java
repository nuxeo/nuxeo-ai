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
package org.nuxeo.ai.enrichment;

import java.io.IOException;
import java.util.UUID;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.pipes.services.PipelineServiceImpl;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.SimpleFeature;

/**
 * Sets the configuration for Enrichment tests
 */
@Deploy({"org.nuxeo.runtime.stream", "org.nuxeo.runtime.stream.pipes.nuxeo-pipes",
        "org.nuxeo.ecm.default.config",
        "org.nuxeo.ai.ai-core:OSGI-INF/enrichment-stream-config-test.xml",
        "org.nuxeo.ai.ai-core"})
public class EnrichmentTestFeature extends SimpleFeature {

    public static final String PIPES_TEST_CONFIG = "test_enrichment";

    @Override
    public void beforeRun(FeaturesRunner runner) throws Exception {
        super.beforeRun(runner);
        Framework.getProperties().put(PipelineServiceImpl.PIPES_CONFIG, PIPES_TEST_CONFIG);
    }

    /**
     * Create a BlobTextStream test object using the specified file blob.
     */
    public static BlobTextStream setupBlobForStream(BlobManager manager, String fileName, String mimeType) throws IOException {
        BlobProvider blobProvider = manager.getBlobProvider("test");
        Blob blob = Blobs.createBlob(manager.getClass().getResourceAsStream(fileName), mimeType);
        ManagedBlob managedBlob = new BlobMetaImpl("test", blob.getMimeType(), blobProvider.writeBlob(blob),
                                                   blob.getDigest(), blob.getEncoding(), blob.getLength());
        BlobTextStream blobTextStream = new BlobTextStream();
        blobTextStream.setRepositoryName("test");
        blobTextStream.setId(UUID.randomUUID().toString());
        blobTextStream.setBlob(managedBlob);
        return blobTextStream;
    }

    /**
     * Create a BlobTextStream test object with a sample test image.
     */
    public static BlobTextStream blobTestImage(BlobManager manager) throws IOException {
        return setupBlobForStream(manager, "/files/plane.jpg", "image/jpeg");
    }
}
