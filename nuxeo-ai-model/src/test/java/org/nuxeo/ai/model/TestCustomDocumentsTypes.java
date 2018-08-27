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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@Deploy("org.nuxeo.ai.ai-model")
public class TestCustomDocumentsTypes {
    @Inject
    CoreSession session;

    @SuppressWarnings("unchecked")
    @Test
    public void testModelDocument() {
        DocumentModel doc = session.createDocumentModel("/", "testModel", "AI_Model");
        doc = session.createDocument(doc);

        // Set all the elements and save the document
        // First, check simple elements
        String nameValueString = "myModel1";
        doc.setPropertyValue(AiDocumentTypeConstants.MODEL_NAME, nameValueString);

        Double accuracyValue = 0.82D;
        doc.setPropertyValue(AiDocumentTypeConstants.MODEL_ACCURACY, accuracyValue);

        // Now check complex types
        Map<String, Object> newInput = new HashMap<>();
        newInput.put("name", "question");
        newInput.put("type", "txt");
        doc.setPropertyValue(AiDocumentTypeConstants.MODEL_INPUTS, (Serializable) Collections.singletonList(newInput));

        newInput.put("multi_class", Boolean.TRUE);
        doc.setPropertyValue(AiDocumentTypeConstants.MODEL_OUTPUTS, (Serializable) Collections.singletonList(newInput));

        DocumentModel corpusDoc = session.createDocumentModel("/", "", "AI_Corpus");
        corpusDoc = session.saveDocument(corpusDoc);
        doc.setPropertyValue(AiDocumentTypeConstants.MODEL_TRAINING_DATA, (Serializable) Collections.singletonList(corpusDoc.getId()));
        doc.setPropertyValue(AiDocumentTypeConstants.MODEL_EVALUATION_DATA, (Serializable) Collections.singletonList(corpusDoc.getId()));

        doc = session.saveDocument(doc);

        // do all the asserts now, on the saved document
        String returnString = (String) doc.getPropertyValue(AiDocumentTypeConstants.MODEL_NAME);
        assertEquals(returnString, nameValueString);
        Double returnLong = (Double) doc.getPropertyValue(AiDocumentTypeConstants.MODEL_ACCURACY);
        assertEquals(returnLong, accuracyValue);

        List<Map<String, Object>> inputsDataRes = (List<Map<String, Object>>) doc.getPropertyValue(AiDocumentTypeConstants.MODEL_INPUTS);
        assertEquals(1, inputsDataRes.size());
        assertEquals("question", (inputsDataRes.get(0)).get("name"));
        assertEquals("txt",  (inputsDataRes.get(0)).get("type"));

        List<Map<String, Object>> outputsDataRes = (List<Map<String, Object>>) doc.getPropertyValue(AiDocumentTypeConstants.MODEL_OUTPUTS);
        assertEquals(1, inputsDataRes.size());
        assertEquals("question", (outputsDataRes.get(0)).get("name"));
        assertEquals("txt", (outputsDataRes.get(0)).get("type"));
        assertEquals(Boolean.TRUE, (outputsDataRes.get(0)).get("multi_class"));

        String[] resDocList = (String[]) doc.getPropertyValue(AiDocumentTypeConstants.MODEL_TRAINING_DATA);
        assertEquals(corpusDoc.getId(), resDocList[0]);

        resDocList = (String[]) doc.getPropertyValue(AiDocumentTypeConstants.MODEL_EVALUATION_DATA);
        assertEquals(corpusDoc.getId(), resDocList[0]);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCorpusDocument() {
        DocumentModel doc = session.createDocumentModel("/", "corpora", "AI_Corpus");
        doc = session.createDocument(doc);

        // First, add simple elements
        String dataLocationValueString = "s3://coolBucket/myData.tfrecord";
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_DATA_LOCATION, dataLocationValueString);
        Long documentsCountValue = 1000L;
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_DOCUMENTS_COUNT, documentsCountValue);
        boolean documentsTrDataValue = true;
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_TRAINING_DATA, documentsTrDataValue);
        // Now add complex types
        Map<String, Object> newInput = new HashMap<>();
        newInput.put("name", "question");
        newInput.put("type", "txt");
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_INPUT_FIELDS, (Serializable) Collections.singletonList(newInput));
        newInput.put("multi_class", Boolean.TRUE);
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_OUTPUT_FIELDS, (Serializable) Collections.singletonList(newInput));

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
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_INPUT_HISTOGRAM, (Serializable) inputsHistData);

        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_OUTPUT_HISTOGRAM, (Serializable) inputsHistData);


        doc = session.saveDocument(doc);

        // First, check simple elements
        String returnString = (String) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_DATA_LOCATION);
        assertEquals(returnString, dataLocationValueString);
        Long returnLong = (Long) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_DOCUMENTS_COUNT);
        assertEquals(returnLong, documentsCountValue);
        Boolean returnBool = (Boolean) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_TRAINING_DATA);
        assertEquals(returnBool, documentsTrDataValue);

        // Now check complex types
        List<Map<String, Object>> inputsDataRes = (List<Map<String, Object>>) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_INPUT_FIELDS);
        assertEquals(1, inputsDataRes.size());
        assertEquals("question", inputsDataRes.get(0).get("name"));
        assertEquals("txt", inputsDataRes.get(0).get("type"));

        List<Map<String, Object>> outputsDataRes = (List<Map<String, Object>>) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_OUTPUT_FIELDS);
        assertEquals(1, inputsDataRes.size());
        assertEquals("question", outputsDataRes.get(0).get("name"));
        assertEquals("txt", outputsDataRes.get(0).get("type"));
        assertEquals(Boolean.TRUE, outputsDataRes.get(0).get("multi_class"));

        List<Map<String, Object>> inputHistDataRes = (List<Map<String, Object>>) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_INPUT_HISTOGRAM);
        assertEquals(2, inputHistDataRes.size());
        assertEquals(inputHistDataRes.get(0), firstFieldLabel);
        assertEquals(inputHistDataRes.get(1), secondFieldLabel);

        List<Map<String, Object>> outputHistDataRes = (List<Map<String, Object>>) doc.getPropertyValue(AiDocumentTypeConstants.CORPUS_OUTPUT_HISTOGRAM);
        assertEquals(2, outputHistDataRes.size());
        assertEquals(inputHistDataRes.get(0), firstFieldLabel);
        assertEquals(inputHistDataRes.get(1), secondFieldLabel);
    }

}
