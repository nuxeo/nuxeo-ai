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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import javax.inject.Inject;
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

    @SuppressWarnings("unchecked")
    @Test
    public void testCorpusDocument() {
        DocumentModel doc = session.createDocumentModel("/", "corpora", "AI_Corpus");
        doc = session.createDocument(doc);

        // First, add simple elements
        String dataLocationValueString = "s3://coolBucket/myData.tfrecord";
        String jobId = "xyz";
        String query = "SELECT * FROM Document WHERE ecm:primaryType = 'Note'";
        Long split = 66L;
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_TRAINING_DATA, dataLocationValueString);
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_EVALUATION_DATA, dataLocationValueString);
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

        List<Map<String, Object>> inputsHistData = new ArrayList<>(1);
        Map<String, Object> firstFieldLabel = new HashMap<>();
        firstFieldLabel.put("field", "question");
        firstFieldLabel.put("label", "B1");
        firstFieldLabel.put("count", 20L);
        inputsHistData.add(firstFieldLabel);
        Map<String, Object> secondFieldLabel = new HashMap<>();
        secondFieldLabel.put("field", "question");
        secondFieldLabel.put("label", "B2");
        secondFieldLabel.put("count", 40L);
        inputsHistData.add(secondFieldLabel);
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_FEATURES_HISTOGRAM, (Serializable) inputsHistData);

        doc = session.saveDocument(doc);

        // First, check simple elements
        String returnString = (String) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_TRAINING_DATA);
        assertEquals(returnString, dataLocationValueString);
        returnString = (String) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_EVALUATION_DATA);
        assertEquals(returnString, dataLocationValueString);
        returnString = (String) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_JOBID);
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

        List<Map<String, Object>> inputHistDataRes =
                (List<Map<String, Object>>) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_FEATURES_HISTOGRAM);
        assertEquals(2, inputHistDataRes.size());
        assertEquals(inputHistDataRes.get(0), firstFieldLabel);
        assertEquals(inputHistDataRes.get(1), secondFieldLabel);
    }

    protected void assertFeatures(List<Map<String, Object>> dataFeatures) {
        assertEquals(1, dataFeatures.size());
        assertEquals("question", dataFeatures.get(0).get("name"));
        assertEquals("txt", dataFeatures.get(0).get("type"));
    }

}
