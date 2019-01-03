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
 *     Pedro Cardoso
 */
package org.nuxeo.ai.model;

import static org.junit.Assert.assertEquals;
import static org.nuxeo.ai.model.serving.TestModelServing.createTestBlob;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import javax.inject.Inject;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy("org.nuxeo.ai.ai-model")
public class TestCustomDocumentsTypes {

    @Inject
    CoreSession session;

    @Inject
    protected BlobManager manager;

    @SuppressWarnings("unchecked")
    @Test
    public void testCorpusDocument() throws IOException {
        DocumentModel doc = session.createDocumentModel("/", "corpora", "AI_Corpus");
        doc = session.createDocument(doc);
        ManagedBlob managedBlob = createTestBlob(manager);

        String jobId = "xyz";
        String query = "SELECT * FROM Document WHERE ecm:primaryType = 'Note'";
        Long split = 66L;
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_TRAINING_DATA, (Serializable) managedBlob);
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_EVALUATION_DATA, (Serializable) managedBlob);
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_STATS, (Serializable) Blobs.createJSONBlob("{}"));
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_JOBID, jobId);
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_SPLIT, split);
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_QUERY, query);
        Long documentsCountValue = 1000L;
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_DOCUMENTS_COUNT, documentsCountValue);

        // Now add complex types
        Map<String, Object> newInput = new HashMap<>();
        newInput.put("name", "question");
        newInput.put("type", "txt");
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_INPUTS, (Serializable) Collections.singletonList(newInput));
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_OUTPUTS, (Serializable) Collections.singletonList(newInput));

        doc = session.saveDocument(doc);

        // First, check simple elements
        Blob trainingData = (Blob) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_TRAINING_DATA);
        assertEquals(managedBlob.getLength(), trainingData.getLength());
        Blob evalData = (Blob) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_EVALUATION_DATA);
        assertEquals(managedBlob.getLength(), evalData.getLength());
        Blob statData = (Blob) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_STATS);
        assertEquals(JSONBlob.APPLICATION_JSON, statData.getMimeType());
        String returnString = (String) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_JOBID);
        assertEquals(jobId, returnString);
        returnString = (String) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_QUERY);
        assertEquals(query, returnString);
        Long returnLong = (Long) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_DOCUMENTS_COUNT);
        assertEquals(documentsCountValue, returnLong);
        returnLong = (Long) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_SPLIT);
        assertEquals(split, returnLong);

        // Now check complex types
        List<Map<String, Object>> dataFeatures =
                (List<Map<String, Object>>) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_INPUTS);
        assertFeatures(dataFeatures);
        dataFeatures =
                (List<Map<String, Object>>) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_OUTPUTS);
        assertFeatures(dataFeatures);
    }

    protected void assertFeatures(List<Map<String, Object>> dataFeatures) {
        assertEquals(1, dataFeatures.size());
        assertEquals("question", dataFeatures.get(0).get("name"));
        assertEquals("txt", dataFeatures.get(0).get("type"));
    }

}
