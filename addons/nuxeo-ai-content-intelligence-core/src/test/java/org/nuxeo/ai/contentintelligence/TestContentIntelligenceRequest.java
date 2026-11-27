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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.hyland.sdk.cic.ke.object.Action;
import org.hyland.sdk.cic.ke.object.ActionConfig;
import org.hyland.sdk.cic.ke.object.ProcessRequest;
import org.junit.Test;

public class TestContentIntelligenceRequest {

    @Test
    public void shouldAcceptApiAndConfigurationActionNames() {
        for (Action action : Action.values()) {
            String kebab = action.name().toLowerCase(Locale.ROOT).replace('_', '-');
            assertEquals(kebab, List.of(action), ContentIntelligenceRequest.of(kebab, null, null).actions());
            assertEquals(action.value(), List.of(action),
                    ContentIntelligenceRequest.of(action.value(), null, null).actions());
        }
    }

    @Test
    public void shouldMapTheActionsUsedByTheNuxeoAiConfiguration() {
        ContentIntelligenceRequest request = ContentIntelligenceRequest.of(
                "image-description, named-entity-recognition-image,text-summarization,named-entity-recognition-text,"
                        + "image-embeddings,image-metadata-generation",
                null, null);
        assertEquals(List.of(Action.IMAGE_DESCRIPTION, Action.NAMED_ENTITY_RECOGNITION_IMAGE,
                Action.TEXT_SUMMARIZATION, Action.NAMED_ENTITY_RECOGNITION_TEXT, Action.IMAGE_EMBEDDINGS,
                Action.IMAGE_METADATA_GENERATION), request.actions());
    }

    @Test
    public void shouldRemoveDuplicateActions() {
        assertEquals(List.of(Action.IMAGE_DESCRIPTION),
                ContentIntelligenceRequest.of("image-description,imageDescription, image-description", null, null)
                                          .actions());
    }

    @Test
    public void shouldRefuseUnknownOrMissingActions() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ContentIntelligenceRequest.of("image-description,imageTelepathy", null, null));
        assertTrue(e.getMessage(), e.getMessage().contains("imageTelepathy"));
        assertThrows(IllegalArgumentException.class, () -> ContentIntelligenceRequest.of(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ContentIntelligenceRequest.of(" , ", null, null));
    }

    @Test
    public void shouldSortActionNamesForFingerprinting() {
        assertEquals(List.of("imageDescription", "namedEntityRecognitionImage"),
                ContentIntelligenceRequest.of("named-entity-recognition-image,image-description", null, null)
                                          .actionNames());
    }

    @Test
    public void shouldMapSimilarMetadata() {
        ContentIntelligenceRequest request = ContentIntelligenceRequest.of("text-metadata-generation",
                "[{\"title\":\"Invoice\",\"pages\":3},{\"title\":\"Contract\"}]", null);
        assertEquals(ActionConfig.builder()
                                 .kSimilarMetadata(List.of(Map.of("title", "Invoice", "pages", "3"),
                                         Map.of("title", "Contract")))
                                 .build(),
                request.actionConfig());
    }

    @Test
    public void shouldRefuseMalformedSimilarMetadata() {
        assertThrows(IllegalArgumentException.class,
                () -> ContentIntelligenceRequest.of("image-description", "{\"title\":\"x\"}", null));
        assertThrows(IllegalArgumentException.class,
                () -> ContentIntelligenceRequest.of("image-description", "[{\"title\":{\"a\":1}}]", null));
        assertThrows(IllegalArgumentException.class,
                () -> ContentIntelligenceRequest.of("image-description", "[{bad", null));
    }

    @Test
    public void shouldMapSupportedExtraPayloadKeys() {
        ContentIntelligenceRequest request = ContentIntelligenceRequest.of("text-summarization", null,
                "{\"maxWordCount\":50,\"model\":\"m1\",\"category\":\"legal\",\"instructions\":{\"tone\":\"formal\"},"
                        + "\"saveResultInContentLakeRepository\":true,\"unsupported\":1}");
        assertEquals(ActionConfig.builder()
                                 .maxWordCount(50)
                                 .model("m1")
                                 .category("legal")
                                 .instruction("tone", "formal")
                                 .build(),
                request.actionConfig());
        assertTrue(request.saveResultInContentLakeRepository());
    }

    @Test
    public void shouldRefuseMalformedExtraPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> ContentIntelligenceRequest.of("text-summarization", null, "[1]"));
        assertThrows(IllegalArgumentException.class,
                () -> ContentIntelligenceRequest.of("text-summarization", null, "{\"maxWordCount\":\"many\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> ContentIntelligenceRequest.of("text-summarization", null, "{\"instructions\":[\"x\"]}"));
    }

    @Test
    public void shouldAcceptNumericStringMaxWordCount() {
        assertEquals(ActionConfig.builder().maxWordCount(50).build(),
                ContentIntelligenceRequest.of("text-summarization", null, "{\"maxWordCount\":\" 50\"}").actionConfig());
    }

    @Test
    public void shouldRefuseNonScalarModelOrCategory() {
        assertThrows(IllegalArgumentException.class,
                () -> ContentIntelligenceRequest.of("text-summarization", null, "{\"model\":{\"id\":\"m1\"}}"));
        assertThrows(IllegalArgumentException.class,
                () -> ContentIntelligenceRequest.of("text-summarization", null, "{\"category\":[\"legal\"]}"));
    }

    @Test
    public void shouldConfigureProcessRequest() {
        ContentIntelligenceRequest request = ContentIntelligenceRequest.of(
                "image-description,named-entity-recognition-image", null, "{\"maxWordCount\":20}");
        ProcessRequest.Builder builder = ProcessRequest.builder().objectPath("path/ok1");
        request.configure(builder);
        ProcessRequest processRequest = builder.build();

        ActionConfig config = ActionConfig.builder().maxWordCount(20).build();
        assertEquals(Map.of("imageDescription", config, "namedEntityRecognitionImage", config),
                processRequest.actions());
        assertFalse(processRequest.saveResultInContentLakeRepository());
    }
}
