/*
 * (C) Copyright 2026 Hyland (http://hyland.com/) and others.
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
 */
package org.nuxeo.ai.contentintelligence;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hyland.sdk.cic.ke.object.ActionResult;
import org.hyland.sdk.cic.ke.object.ClassificationResult;
import org.hyland.sdk.cic.ke.object.EnrichmentResult;
import org.hyland.sdk.cic.ke.object.EnrichmentResultEntry;
import org.hyland.sdk.cic.ke.object.ImageClassificationResult;
import org.hyland.sdk.cic.ke.object.ImageDescription;
import org.hyland.sdk.cic.ke.object.ImageEmbedding;
import org.hyland.sdk.cic.ke.object.ImageMetadata;
import org.hyland.sdk.cic.ke.object.NamedEntities;
import org.hyland.sdk.cic.ke.object.ProcessingError;
import org.hyland.sdk.cic.ke.object.ProcessingErrorType;
import org.hyland.sdk.cic.ke.object.TextClassificationResult;
import org.hyland.sdk.cic.ke.object.TextEmbedding;
import org.hyland.sdk.cic.ke.object.TextMetadata;
import org.hyland.sdk.cic.ke.object.TextSummary;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestContentIntelligenceResultSerializer {

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected static JsonNode json(String json) throws Exception {
        return MAPPER.readTree(json.replace('\'', '"'));
    }

    protected static EnrichmentResultEntry entry(ActionResult<ImageDescription> imageDescription,
            ActionResult<ImageMetadata> imageMetadata, ActionResult<TextMetadata> textMetadata,
            ActionResult<TextSummary> textSummary, ActionResult<TextClassificationResult> textClassification,
            ActionResult<ImageClassificationResult> imageClassification, ActionResult<TextEmbedding> textEmbeddings,
            ActionResult<ImageEmbedding> imageEmbeddings, ActionResult<NamedEntities> namedEntityText,
            ActionResult<NamedEntities> namedEntityImage, ActionResult<ClassificationResult> pretrained,
            List<ProcessingError> errors) {
        return new EnrichmentResultEntry("ok1", imageDescription, imageMetadata, textMetadata, textSummary,
                textClassification, imageClassification, textEmbeddings, imageEmbeddings, namedEntityText,
                namedEntityImage, pretrained, errors);
    }

    @Test
    public void shouldWriteEverySuccessfulResultType() throws Exception {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", "Invoice");
        metadata.put("pages", 3);
        Map<String, List<String>> entities = new LinkedHashMap<>();
        entities.put("persons", List.of("Jane Doe"));
        entities.put("locations", List.of("Paris", "Rome"));
        EnrichmentResultEntry entry = entry(ActionResult.success(new ImageDescription("A cat")),
                ActionResult.success(new ImageMetadata(metadata)), ActionResult.success(new TextMetadata(metadata)),
                ActionResult.success(new TextSummary("A summary")),
                ActionResult.success(new TextClassificationResult("contract")),
                ActionResult.success(new ImageClassificationResult("animal")),
                ActionResult.success(new TextEmbedding(List.of(List.of(0.1, 0.2), List.of(0.3)))),
                ActionResult.success(new ImageEmbedding(List.of(0.5, 0.6))),
                ActionResult.success(new NamedEntities(entities)), ActionResult.success(new NamedEntities(entities)),
                ActionResult.success(new ClassificationResult("invoice", 0.93)), List.of());
        EnrichmentResult result = new EnrichmentResult("job-1", "2026-11-27T10:00:00Z", List.of(entry), "SUCCESS",
                false);

        String expected = "{'id':'job-1','timestamp':'2026-11-27T10:00:00Z','status':'SUCCESS','results':[{"
                + "'objectKey':'ok1',"
                + "'imageDescription':{'isSuccess':true,'result':'A cat'},"
                + "'imageMetadata':{'isSuccess':true,'result':{'title':'Invoice','pages':3}},"
                + "'textMetadata':{'isSuccess':true,'result':{'title':'Invoice','pages':3}},"
                + "'textSummary':{'isSuccess':true,'result':'A summary'},"
                + "'textClassification':{'isSuccess':true,'result':'contract'},"
                + "'imageClassification':{'isSuccess':true,'result':'animal'},"
                + "'textEmbeddings':{'isSuccess':true,'result':[[0.1,0.2],[0.3]]},"
                + "'imageEmbeddings':{'isSuccess':true,'result':[0.5,0.6]},"
                + "'namedEntityText':{'isSuccess':true,"
                + "'result':{'persons':['Jane Doe'],'locations':['Paris','Rome']}},"
                + "'namedEntityImage':{'isSuccess':true,"
                + "'result':{'persons':['Jane Doe'],'locations':['Paris','Rome']}},"
                + "'pretrainedClassification':{'isSuccess':true,"
                + "'result':{'classification':'invoice','confidence':0.93}}"
                + "}]}";
        assertEquals(json(expected), ContentIntelligenceResultSerializer.toJson(result));
    }

    @Test
    public void shouldWriteFailuresAndGeneralErrors() throws Exception {
        EnrichmentResultEntry entry = entry(
                ActionResult.failure(new ProcessingError(ProcessingErrorType.UNEXPECTED_ERROR, "boom")),
                ActionResult.failure(new ProcessingError(ProcessingErrorType.UNKNOWN, "odd")),
                ActionResult.failure(null), ActionResult.success(null), null, null, null, null, null, null, null,
                List.of(new ProcessingError(ProcessingErrorType.TIMEOUT, "too slow")));
        EnrichmentResult result = new EnrichmentResult("job-2", null, List.of(entry), "PARTIAL_FAILURE", false);

        String expected = "{'id':'job-2','status':'PARTIAL_FAILURE','results':[{'objectKey':'ok1',"
                + "'imageDescription':{'isSuccess':false,'error':{'errorType':'UnexpectedError','message':'boom'}},"
                + "'imageMetadata':{'isSuccess':false,'error':{'message':'odd'}},"
                + "'textMetadata':{'isSuccess':false},"
                + "'textSummary':{'isSuccess':true},"
                + "'generalProcessingErrors':[{'errorType':'Timeout','message':'too slow'}]"
                + "}]}";
        assertEquals(json(expected), ContentIntelligenceResultSerializer.toJson(result));
    }

    @Test
    public void shouldWriteInProgressResult() throws Exception {
        EnrichmentResult result = new EnrichmentResult("job-3", null, List.of(), "PROCESSING", true);
        assertEquals(json("{'id':'job-3','status':'PROCESSING','results':[]}"),
                ContentIntelligenceResultSerializer.toJson(result));
    }

    @Test
    public void shouldOmitMissingStatus() throws Exception {
        EnrichmentResult result = new EnrichmentResult("job-4", null, null, null, false);
        assertEquals(json("{'id':'job-4','results':[]}"), ContentIntelligenceResultSerializer.toJson(result));
    }
}
