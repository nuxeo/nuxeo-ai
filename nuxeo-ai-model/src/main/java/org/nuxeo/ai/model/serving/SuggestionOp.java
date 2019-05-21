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

import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import java.io.IOException;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.metadata.Suggestion;
import org.nuxeo.ai.metadata.SuggestionMetadata;
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
import com.fasterxml.jackson.core.JsonGenerator;

/**
 * Suggests metadata for the specified document.
 */
@Operation(id = SuggestionOp.ID, category = Constants.CAT_DOCUMENT, label = "Ask for a suggestion.",
        description = "Calls intelligent services on the provided document and returns suggested metadata.")
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

    @Param(name = "document", description = "A document", required = false)
    protected DocumentModel documentModel;

    @OperationMethod
    public Blob run(DocumentModel doc) throws IOException {

        List<SuggestionMetadata> suggestions;
        if (doc == null || (suggestions = modelServingService.predict(doc)) == null || suggestions.isEmpty()) {
            return Blobs.createJSONBlob(EMPTY_JSON_LIST);
        }

        String toReturn = toJsonString(jg -> {
            // JsonGenerator won't let you start with an array, so I am wrapping it in a field and removing the
            // field at the end of the method.
            jg.writeFieldName("return");
            jg.writeStartArray();
            suggestions.forEach(metadata -> {
                try {
                    writeProperties(metadata, doc, jg);
                } catch (IllegalArgumentException e) {
                    log.debug("Document {} error. {}", doc.getId(), e.getMessage());
                }
            });
            jg.writeEndArray();
        });
        return Blobs.createJSONBlob(toReturn.substring(10, toReturn.length() - 1));
    }

    /**
     * Write property information alongside suggestions.
     */
    protected void writeProperties(SuggestionMetadata metadata, DocumentModel input, JsonGenerator jg) {

        RenderingContext ctx = RenderingContext.CtxBuilder.fetchInDoc("properties").get();
        ctx.setParameterValues("document", input);
        DocumentPropertyJsonWriter writer = registry.getInstance(ctx, DocumentPropertyJsonWriter.class);
        if (writer != null) {
            try {

                jg.writeStartObject();  // 1
                jg.writeStringField("serviceName", metadata.getServiceName());
                jg.writeArrayFieldStart("suggestions");  // a1
                for (Suggestion suggestion : metadata.getSuggestions()) {
                    Property property = input.getProperty(suggestion.getProperty());
                    jg.writeStartObject();  // 2
                    jg.writeStringField("property", suggestion.getProperty());
                    jg.writeArrayFieldStart("values");  // a2
                    for (AIMetadata.Label label : suggestion.getValues()) {
                        jg.writeStartObject();  // 3
                        jg.writeStringField("confidence", String.valueOf(label.getConfidence()));
                        jg.writeFieldName("value");
                        Property prop = setProperty(property.getField(), label);
                        writer.write(prop, jg);
                        jg.writeEndObject();  // 3
                    }
                    jg.writeEndArray();   // a2
                    jg.writeEndObject();  // 2
                }
                jg.writeEndArray();  // a1
                jg.writeEndObject(); // 1
            } catch (PropertyException | IOException e) {
                log.error("Failed to write property. ", e);
            }
        }
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

    @OperationMethod
    public Blob run(DocumentRef docRef) throws IOException {
        DocumentModel docModel = coreSession.getDocument(docRef);
        return run(docModel);
    }

    @OperationMethod
    public Blob run() throws IOException {
        return run(documentModel);
    }
}
