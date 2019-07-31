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
package org.nuxeo.ai.cloud;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.nuxeo.ai.cloud.NuxeoCloudClient.API_AI;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_TYPE;
import static org.nuxeo.ai.model.serving.TestModelServing.createTestBlob;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ai.model.AiDocumentTypeConstants;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy("org.nuxeo.ai.ai-model")
public class CloudClientTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(
            options().extensions(new ResponseTemplateTransformer(true)).port(5089));

    @Inject
    protected CoreSession session;

    @Inject
    protected BlobManager manager;

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected CloudClient client;

    @Test
    public void testClient() {

        assertFalse(client.isAvailable());
        // Doesn't do anything because its not configured.
        assertNull(client.uploadedDataset(session.createDocumentModel("/", "not_used", DATASET_EXPORT_TYPE)));

        try {
            ((NuxeoCloudClient) client).configureClient(new CloudConfigDescriptor());
            fail();
        } catch (IllegalArgumentException e) {
            // Success
        }

    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void testConfiguredSuccess() throws IOException {
        assertNotNull(client.uploadedDataset(testDocument()));
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-bad-test.xml")
    public void testConfiguredFails() throws IOException {
        assertNull(client.uploadedDataset(testDocument()));
    }

    protected DocumentModel testDocument() throws IOException {
        ManagedBlob managedBlob = createTestBlob(manager);

        // Create a document
        DocumentModel doc = session.createDocumentModel("/", "corpora", DATASET_EXPORT_TYPE);
        doc = session.createDocument(doc);
        String jobId = "testing1";
        String query = "SELECT * FROM Document WHERE ecm:primaryType = 'Note'";
        Long split = 77L;
        doc.setPropertyValue(AiDocumentTypeConstants.DATASET_EXPORT_JOB_ID, jobId);
        doc.setPropertyValue(AiDocumentTypeConstants.DATASET_EXPORT_SPLIT, split);
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", "dc:title");
        fields.put("type", "txt");
        doc.setPropertyValue(AiDocumentTypeConstants.DATASET_EXPORT_INPUTS, (Serializable) Collections.singletonList(fields));
        fields.put("name", "dc:creator");
        doc.setPropertyValue(AiDocumentTypeConstants.DATASET_EXPORT_OUTPUTS, (Serializable) Collections.singletonList(fields));
        doc.setPropertyValue(AiDocumentTypeConstants.DATASET_EXPORT_QUERY, query);
        doc.setPropertyValue(AiDocumentTypeConstants.DATASET_EXPORT_TRAINING_DATA, (Serializable) managedBlob);
        doc.setPropertyValue(AiDocumentTypeConstants.DATASET_EXPORT_EVALUATION_DATA, (Serializable) managedBlob);
        doc.setPropertyValue(AiDocumentTypeConstants.DATASET_EXPORT_STATS, (Serializable) managedBlob);
        Long documentsCountValue = 1000L;
        doc.setPropertyValue(AiDocumentTypeConstants.DATASET_EXPORT_DOCUMENTS_COUNT, documentsCountValue);
        doc = session.createDocument(doc);
        txFeature.nextTransaction();
        return doc;
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void testGetPut() throws IOException {
        String result = client.get(API_AI + client.byProjectId("/models?enrichers.document=children"),
                response -> response.isSuccessful() ? response.body().string() : null);

        String result2 = client.getByProject("/models?enrichers.document=children",
                response -> response.isSuccessful() ? response.body().string() : null);

        assertEquals(result, result2);

        String putBody = "could be anything";
        String resBody = client.put(client.byProjectId("/models"), putBody,
                response -> response.isSuccessful() ? response.body().string() : null);
        assertTrue(resBody.contains(putBody));
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void shouldGetModelsFromCloud() throws IOException {
        JSONBlob models = client.getCloudAIModels(session);
        assertThat(models.toString()).isNotEmpty();
    }

    @Test
    @Deploy("org.nuxeo.ai.ai-model:OSGI-INF/cloud-client-test.xml")
    public void shouldGetCorpusFromCloud() throws IOException {
        JSONBlob models = client.getCloudAIModels(session);
        assertThat(models.toString()).isNotEmpty();
    }

    @Test
    public void testTitle() {
        NuxeoCloudClient nuxClient = (NuxeoCloudClient) client;
        assertEquals("2 features, 34 Training, 56 Evaluation, Export id xyz", nuxClient.makeTitle(34, 56, "xyz", 2));
        assertEquals("0 features, 100 Training, 206 Evaluation, Export id xyzx",
                nuxClient.makeTitle(100, 206, "xyzx", 0));
    }
}
