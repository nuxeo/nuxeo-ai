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
package org.nuxeo.ai.model.serving;

import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.LabelSuggestion;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.impl.DocumentPartImpl;
import org.nuxeo.ecm.core.api.model.impl.PropertyFactory;
import org.nuxeo.ecm.core.io.marshallers.json.document.DocumentPropertyJsonWriter;
import org.nuxeo.ecm.core.io.registry.MarshallerRegistry;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.Schema;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.core.JsonGenerator;

/**
 * Suggests metadata for the specified document.
 */
@Operation(id = SuggestionOp.ID, category = Constants.CAT_DOCUMENT, label = "Ask for a suggestion.", description = "Calls intelligent services on the provided document and returns suggested metadata.")
public class SuggestionOp {

    public static final String ID = "AI.Suggestion";

    public static final String EMPTY_JSON_LIST = "[]";

    private static final Logger log = LogManager.getLogger(SuggestionOp.class);

    @Context
    public CoreSession coreSession;

    @Context
    protected ModelServingService modelServingService;

    @Context
    protected MarshallerRegistry registry;

    @Param(name = "updatedDocument", description = "Document with changes done on client side before saving", required = false)
    protected DocumentModel updatedDoc;

    @Param(name = "references", description = "Should the entity references be resolved?", required = false)
    protected boolean references = false;

    @OperationMethod
    public Blob run(DocumentRef docRef) {
        DocumentModel docModel = coreSession.getDocument(docRef);
        return run(docModel);
    }

    @OperationMethod
    public Blob run(DocumentModel doc) {
        if (updatedDoc != null) {
            for (String schema : doc.getSchemas()) {
                for (Property prop : updatedDoc.getPropertyObjects(schema)) {
                    doc.setPropertyValue(prop.getName(), prop.getValue());
                }
            }
        }

        List<EnrichmentMetadata> suggestions = modelServingService.predict(doc);
        if (suggestions == null || suggestions.isEmpty()) {
            return Blobs.createJSONBlob(EMPTY_JSON_LIST);
        }

        ByteArrayOutputStream outWriter = new ByteArrayOutputStream();
        DocumentPropertyJsonWriter writer = references
                ? registry.getInstance(RenderingContext.CtxBuilder.fetchInDoc("properties").get(),
                DocumentPropertyJsonWriter.class)
                : null;
        try (JsonGenerator jg = MAPPER.getFactory().createGenerator(outWriter)) {
            List<SuggestionsAsJson> suggestionsAsJson = suggestions.stream()
                    .map(metadata -> writeJson(metadata, doc, writer,
                            outWriter, jg))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return Blobs.createJSONBlob(MAPPER.writeValueAsString(suggestionsAsJson));
        } catch (IOException e) {
            throw new NuxeoException("Unable to turn data into a json String", e);
        }
    }

    /**
     * Write property information alongside suggestions.
     */
    protected SuggestionsAsJson writeJson(EnrichmentMetadata metadata, DocumentModel input,
                                          DocumentPropertyJsonWriter writer, ByteArrayOutputStream outWriter, JsonGenerator jg) {
        try {
            List<SuggestionListAsJson> suggestionList = new ArrayList<>();
            for (LabelSuggestion labelSuggestion : metadata.getLabels()) {
                Property property = input.getProperty(labelSuggestion.getProperty());
                List<SuggestionAsJson> suggestionValues = new ArrayList<>();
                for (AIMetadata.Label label : labelSuggestion.getValues()) {
                    String value = "\"" + label.getName() + "\"";
                    if (writer != null) {
                        Property prop = setProperty(property.getField(), label);
                        outWriter.reset();
                        writer.write(prop, jg);
                        value = outWriter.toString();
                    }
                    suggestionValues.add(new SuggestionAsJson(label.getConfidence(), value));
                }
                suggestionList.add(new SuggestionListAsJson(labelSuggestion.getProperty(), suggestionValues));
            }
            return new SuggestionsAsJson(metadata.getModelName(), suggestionList);
        } catch (PropertyException | IOException | UnsupportedOperationException e) {
            log.error("Failed to write property. ", e);
        }
        return null;
    }

    /**
     * Create and set a property based on the supplied Field.
     */
    protected Property setProperty(Field field, AIMetadata.Label label) {

        Schema schema = field.getDeclaringType().getSchema();
        DocumentPartImpl part = new DocumentPartImpl(schema);
        Property prop = PropertyFactory.createProperty(part, field, Property.NONE);
        if (prop.isScalar()) {
            prop.setValue(label.getName());
        } else if (prop.isList()) {
            ListType t = (ListType) prop.getType();
            prop = PropertyFactory.createProperty(part, t.getField(), Property.NONE);
            prop.setValue(label.getName());
        }
        return prop;
    }

    /**
     * Class used for JSON serialization
     */
    public static class SuggestionsAsJson {
        protected final String modelName;

        protected final List<SuggestionListAsJson> suggestions;

        public SuggestionsAsJson(String modelName, List<SuggestionListAsJson> suggestions) {
            this.modelName = modelName;
            this.suggestions = suggestions;
        }

        public String getModelName() {
            return modelName;
        }

        public List<SuggestionListAsJson> getSuggestions() {
            return suggestions;
        }
    }

    /**
     * Class used for JSON serialization
     */
    public static class SuggestionListAsJson {
        protected final String property;

        protected final List<SuggestionAsJson> values;

        public SuggestionListAsJson(String property, List<SuggestionAsJson> values) {
            this.property = property;
            this.values = values;
        }

        public String getProperty() {
            return property;
        }

        public List<SuggestionAsJson> getValues() {
            return values;
        }
    }

    /**
     * Class used for JSON serialization
     */
    public static class SuggestionAsJson {
        protected final float confidence;

        @JsonRawValue
        protected final String value;

        public SuggestionAsJson(float confidence, String value) {
            this.confidence = confidence;
            this.value = value;
        }

        public float getConfidence() {
            return confidence;
        }

        public String getValue() {
            return value;
        }
    }
}