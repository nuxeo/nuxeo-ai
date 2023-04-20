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

import static org.nuxeo.ai.configuration.ThresholdComponent.AUTOFILL_DEFAULT_VALUE;
import static org.nuxeo.ai.configuration.ThresholdComponent.AUTO_CORRECT_DEFAULT_VALUE;

import java.io.IOException;
import java.util.UUID;
import org.nuxeo.ai.pipes.services.PipelineServiceImpl;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobMetaImpl;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RunnerFeature;

/**
 * Sets the configuration for Enrichment tests
 */
@Deploy({ "org.nuxeo.runtime.stream", "org.nuxeo.ai.nuxeo-ai-pipes", "org.nuxeo.ecm.default.config",
        "org.nuxeo.ai.ai-core:OSGI-INF/enrichment-stream-config-test.xml", "org.nuxeo.ai.ai-core" })
public class EnrichmentTestFeature implements RunnerFeature {

    public static final String PIPES_TEST_CONFIG = "test_enrichment";

    public static final String FILE_CONTENT = "file:content";

    @Override
    public void beforeRun(FeaturesRunner runner) {
        Framework.getProperties().put(PipelineServiceImpl.PIPES_CONFIG, PIPES_TEST_CONFIG);
        Framework.getProperties().put(AUTOFILL_DEFAULT_VALUE, "0.2");
        Framework.getProperties().put(AUTO_CORRECT_DEFAULT_VALUE, "0.4");
    }

    /**
     * Create a BlobTextFromDocument test object using the specified file blob.
     */
    public static BlobTextFromDocument setupBlobForStream(BlobManager manager, String fileName, String mimeType,
            String blobType) throws IOException {
        BlobProvider blobProvider = manager.getBlobProvider("test");
        Blob blob = Blobs.createBlob(manager.getClass().getResourceAsStream(fileName), mimeType);
        ManagedBlob managedBlob = new BlobMetaImpl("test", blob.getMimeType(), blobProvider.writeBlob(blob),
                blob.getDigest(), blob.getEncoding(), blob.getLength());
        BlobTextFromDocument blobTextFromDoc = new BlobTextFromDocument();
        blobTextFromDoc.setRepositoryName("test");
        blobTextFromDoc.setId(UUID.randomUUID().toString());
        blobTextFromDoc.addBlob(FILE_CONTENT, blobType, managedBlob);
        return blobTextFromDoc;
    }

    /**
     * Create a BlobTextFromDocument test object with a sample test image.
     */
    public static BlobTextFromDocument blobTestImage(BlobManager manager) throws IOException {
        return setupBlobForStream(manager, "/files/plane.jpg", "image/jpeg", "img");
    }

    /**
     * Create a BlobTextFromDocument test object with a sample PDF.
     */
    public static BlobTextFromDocument blobTestPdf(BlobManager manager) throws IOException {
        return setupBlobForStream(manager, "/files/MLLecture1.pdf", "application/pdf", "txt");
    }

}
