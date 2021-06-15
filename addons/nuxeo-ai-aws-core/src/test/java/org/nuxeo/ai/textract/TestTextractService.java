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
package org.nuxeo.ai.textract;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.nuxeo.ai.enrichment.EnrichmentTestFeature.setupBlobForStream;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.AWS;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.enrichment.EnrichmentProvider;
import org.nuxeo.ai.enrichment.EnrichmentTestFeature;
import org.nuxeo.ai.pipes.services.JacksonUtil;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import com.amazonaws.services.textract.model.Block;

@RunWith(FeaturesRunner.class)
@Features({ EnrichmentTestFeature.class, RuntimeFeature.class, PlatformFeature.class })
@Deploy("org.nuxeo.runtime.aws")
@Deploy("org.nuxeo.ai.aws.aws-core")
@Deploy("org.nuxeo.ai.aws.aws-core:OSGI-INF/test-textract-config.xml")
public class TestTextractService {

    @Inject
    protected AIComponent aiComponent;

    @Inject
    protected BlobManager manager;

    @Inject
    protected TextractService textractService;

    @Test
    public void testDetectText() throws IOException {
        AWS.assumeCredentials();
        BlobTextFromDocument blobTextFromDoc = setupBlobForStream(manager, "/files/harddrive.jpg", "image/jpeg", "img");

        EnrichmentProvider service = aiComponent.getEnrichmentProvider("aws.documentText");
        assertNotNull(service);
        Collection<EnrichmentMetadata> metadataCollection = service.enrich(blobTextFromDoc);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata metadata = metadataCollection.iterator().next();
        assertNotNull(metadata);
        List<Block> blocks = AWSHelper.getInstance().getTextractBlocks(metadata);
        assertNotNull(blocks);
        assertTrue(blocks.size() > 75);
    }

    @Test
    public void testAnalyzeDocument() throws IOException {
        AWS.assumeCredentials();
        BlobTextFromDocument blobTextFromDoc = setupBlobForStream(manager, "/files/text.png", "image/jpeg", "img");

        EnrichmentProvider service = aiComponent.getEnrichmentProvider("aws.documentAnalyze");
        assertNotNull(service);
        Collection<EnrichmentMetadata> metadataCollection = service.enrich(blobTextFromDoc);
        assertEquals(1, metadataCollection.size());
        EnrichmentMetadata metadata = metadataCollection.iterator().next();
        assertNotNull(metadata);
        List<Block> blocks = AWSHelper.getInstance().getTextractBlocks(metadata);
        assertNotNull(blocks);
        assertTrue(blocks.size() > 75);
    }

    @Test
    public void testBlocks() throws URISyntaxException, IOException {
        Block b = new Block();
        b.setBlockType("LINE");
        String raw = toJsonString(jg -> jg.writeObjectField("blocks", Arrays.asList(b)));

        AnalyzeDocumentResult result = JacksonUtil.MAPPER.readValue(raw, AnalyzeDocumentResult.class);
        List<Block> rawBlock = result.getBlocks();
        assertNotNull(rawBlock);

        File json = FileUtils.getResourceFileFromContext("files/textract.json");
        AnalyzeDocumentResult helper = JacksonUtil.MAPPER.readValue(json, AnalyzeDocumentResult.class);
        List<Block> blocks = helper.getBlocks();
        assertNotNull(blocks);
    }

    @Test
    public void testProcessors() {
        assertNotNull(textractService);
        assertNotNull(textractService.getProcessors("noway"));
        List<TextractProcessor> processors = textractService.getProcessors("testService");
        assertEquals(2, processors.size());

        BlobTextFromDocument blobTextFromDoc = new BlobTextFromDocument("docId", "default", "parent", "File", null);
        EnrichmentMetadata.Builder builder = new EnrichmentMetadata.Builder("b1", "myb", blobTextFromDoc);

        Block b = new Block();
        b.setBlockType("TABLE");
        processors.forEach(p -> p.process(Arrays.asList(b), null, new IdRef(blobTextFromDoc.getId()), builder));

        EnrichmentMetadata metadata = builder.build();
        assertEquals(1, metadata.getLabels().size());
        assertTrue(metadata.getLabels().get(0).getValues().get(0).getName().contains("There are 1 blocks"));
    }

}
