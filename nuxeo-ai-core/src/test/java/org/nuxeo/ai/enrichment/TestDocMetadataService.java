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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.nuxeo.ai.AIConstants.AI_CREATOR_PROPERTY;
import static org.nuxeo.ai.AIConstants.AI_SERVICE_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_CLASSIFICATIONS;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_KIND_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_LABELS_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_NAME;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_RAW_KEY_PROPERTY;
import static org.nuxeo.ai.AIConstants.ENRICHMENT_TARGET_DOCPROP_PROPERTY;
import static org.nuxeo.ai.AIConstants.NORMALIZED_PROPERTY;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ai.services.DocMetadataService;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.stream.pipes.services.JacksonUtil;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({EnrichmentTestFeature.class, PlatformFeature.class})
@Deploy({"org.nuxeo.ecm.platform.tag", "org.nuxeo.ai.ai-core", "org.nuxeo.ai.ai-core:OSGI-INF/enrichment-test.xml"})
public class TestDocMetadataService {

    @Inject
    protected CoreSession session;

    @Inject
    protected AIComponent aiComponent;

    @Inject
    protected DocMetadataService docMetadataService;

    @Test
    public void TestSavesData() throws IOException {
        assertNotNull(docMetadataService);

        DocumentModel testDoc = session.createDocumentModel("/", "My Test Doc", "File");
        testDoc.setPropertyValue("dc:title", "Testing document");
        testDoc = session.createDocument(testDoc);
        session.save();

        BlobTextStream blobTextStream = new BlobTextStream(testDoc.getId(), testDoc.getRepositoryName(),
                                                           testDoc.getParentRef().toString(), testDoc.getType(),
                                                           testDoc.getFacets()
        );
        String text = "You can change me";
        blobTextStream.setText(text);
        blobTextStream.addXPath("dc:myprop");
        String serviceName = "reverse";
        EnrichmentService service = aiComponent.getEnrichmentService(serviceName);
        EnrichmentMetadata metadata = service.enrich(blobTextStream);

        DocumentModel doc = docMetadataService.saveEnrichment(session, metadata);
        session.save();

        //Now read the values and check them
        String textReversed = StringUtils.reverse(text);
        Property classProp = doc.getPropertyObject(ENRICHMENT_NAME, ENRICHMENT_CLASSIFICATIONS);
        assertNotNull(classProp);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> classifications = classProp.getValue(List.class);
        assertEquals(1, classifications.size());
        Map<String, Object> classification = classifications.get(0);
        assertEquals(serviceName, classification.get(AI_SERVICE_PROPERTY));
        assertEquals(SecurityConstants.SYSTEM_USERNAME, classification.get(AI_CREATOR_PROPERTY));
        assertEquals("/classification/custom", classification.get(ENRICHMENT_KIND_PROPERTY));
        String[] labels = (String[]) classification.get(ENRICHMENT_LABELS_PROPERTY);
        assertEquals(textReversed, labels[0]);
        String[] targetProps = (String[]) classification.get(ENRICHMENT_TARGET_DOCPROP_PROPERTY);
        assertEquals("dc:myprop", targetProps[0]);
        Blob blob = (Blob) classification.get(ENRICHMENT_RAW_KEY_PROPERTY);
        assertNotNull(blob);
        assertEquals(textReversed, blob.getString());
        blob = (Blob) classification.get(NORMALIZED_PROPERTY);
        assertEquals(metadata, JacksonUtil.MAPPER.readValue(blob.getString(), EnrichmentMetadata.class));

    }
}
