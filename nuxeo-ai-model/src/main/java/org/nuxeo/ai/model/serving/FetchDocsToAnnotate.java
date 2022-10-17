/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo
 */
package org.nuxeo.ai.model.serving;

import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.io.marshallers.json.document.DocumentPropertyJsonWriter;
import org.nuxeo.ecm.core.io.registry.MarshallerRegistry;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.core.model.DocumentModelResolver;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.ListTypeImpl;
import org.nuxeo.ecm.core.schema.types.SimpleTypeImpl;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.resolver.ObjectResolver;
import org.nuxeo.ecm.platform.url.io.DocumentUrlJsonEnricher;
import org.nuxeo.ecm.platform.web.common.vh.VirtualHostHelper;
import com.fasterxml.jackson.core.JsonGenerator;

/**
 * Resolves all given properties for given documents <code>
 * Example:
 * <code>
 * [
 * {
 * "docId": "20ac2a8e-8bf0-4958-a6d8-14ed68bddd0b",
 * "inputs": [
 * {
 * "name": "dc:description",
 * "isArray": false,
 * "type": "clob",
 * "value": "Description 11"
 * },
 * {
 * "resolver": "userManagerResolver",
 * "isArray": true,
 * "name": "dc:contributors",
 * "type": "contributorList",
 * "value": [
 * {
 * "entity-type": "user",
 * "id": "system",
 * "extendedGroups": [
 * {
 * "name": "administrators",
 * "label": "Administrators group",
 * "url": "group/administrators"
 * }
 * ],
 * "isAdministrator": true,
 * "isAnonymous": false
 * }
 * ]
 * },
 * {
 * "name": "dc:subjects",
 * "isArray": true,
 * "type": "subjectList",
 * "value": [
 * "art/architecture",
 * "art/danse"
 * ]
 * },
 * {
 * "name": "file:content",
 * "isArray": false,
 * "type": "content",
 * "value": {
 * "name": "nxblob-67186932984972622.tmp",
 * "mime-type": null,
 * "encoding": null,
 * "digestAlgorithm": "MD5",
 * "digest": "144d66206d7e2fd7c7bea9bb34362ff4",
 * "length": "1025580",
 * "data": "http://fake-url.nuxeo.com/nxfile/test/20ac2a8e-8bf0-4958-a6d8-14ed68bddd0b/file:content/nxblob-67186932984972622.tmp?changeToken=0-0"
 * }
 * },
 * {
 * "resolver": "documentResolver",
 * "isArray": false,
 * "name": "extrafile:docprop",
 * "type": "string",
 * "value": {
 * "documentURL": null
 * }
 * }
 * ],
 * "outputs": [...],
 * },
 * ....
 * </code>
 */
@Operation(id = FetchDocsToAnnotate.ID, category = Constants.CAT_DOCUMENT, description = "Fetch document(s) content given properties")
public class FetchDocsToAnnotate {

    public static final String ID = "AI.FetchDocsToAnnotate";

    public static final String EMPTY_JSON_LIST = "[]";

    @Context
    public CoreSession coreSession;

    @Context
    public SchemaManager schemaManager;

    @Context
    protected OperationContext ctx;

    @Param(name = "uids")
    protected StringList uids;

    @Param(name = "inputs")
    protected StringList inputs;

    @Param(name = "outputs")
    protected StringList outputs;

    @Context
    protected MarshallerRegistry registry;

    private static final Logger log = LogManager.getLogger(FetchDocsToAnnotate.class);

    @OperationMethod
    public Blob run() {
        try {
            if (uids.isEmpty()) {
                throw new NuxeoException("Please refer uids");
            }
            DocumentModelList docs = coreSession.query("SELECT * FROM Document WHERE ecm:uuid IN ('" + uids.stream()
                                                                                                           .map(String::valueOf)
                                                                                                           .collect(
                                                                                                                   Collectors.joining(
                                                                                                                           "','"))
                    + "')");
            if (docs.isEmpty()) {
                return Blobs.createJSONBlob(MAPPER.writeValueAsString(EMPTY_JSON_LIST));
            }
            ByteArrayOutputStream outWriter = new ByteArrayOutputStream();
            List<Map<String, Object>> results = new ArrayList<>();
            try (JsonGenerator jg = MAPPER.getFactory().createGenerator(outWriter)) {
                docs.stream()
                    .filter((documentModel) -> coreSession.exists(documentModel.getRef()))
                    .forEach((documentModel) -> {
                        HttpServletRequest request = (HttpServletRequest) ctx.get("request");
                        DocumentPropertyJsonWriter writer = registry.getInstance(
                                RenderingContext.CtxBuilder.param("document", documentModel)
                                                           .fetchInDoc("properties")
                                                           .base(VirtualHostHelper.getBaseURL(request))
                                                           .get(), DocumentPropertyJsonWriter.class);
                        DocumentUrlJsonEnricher enricher = registry.getInstance(
                                RenderingContext.CtxBuilder.param("document", documentModel)
                                                           .session(coreSession)
                                                           .fetchInDoc("properties")
                                                           .base(VirtualHostHelper.getBaseURL(request))
                                                           .get(), DocumentUrlJsonEnricher.class);
                        // workaround to be able to have the data url for a given blob
                        List<Map<String, Object>> inputs = new ArrayList<>();
                        List<Map<String, Object>> outputs = new ArrayList<>();
                        Map<String, Object> documents = new HashMap<>();
                        this.inputs.forEach((propertyName) -> {
                            mapProperties(outWriter, jg, documentModel, writer, enricher, inputs, propertyName);
                        });
                        this.outputs.forEach((propertyName) -> {
                            mapProperties(outWriter, jg, documentModel, writer, enricher, outputs, propertyName);
                        });
                        documents.put("docId", documentModel.getId());
                        documents.put("inputs", inputs);
                        documents.put("outputs", outputs);
                        results.add(documents);
                    });
            }
            return Blobs.createJSONBlob(MAPPER.writeValueAsString(results));
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }

    protected void mapProperties(ByteArrayOutputStream outWriter, JsonGenerator jg, DocumentModel documentModel,
            DocumentPropertyJsonWriter writer, DocumentUrlJsonEnricher enricher, List<Map<String, Object>> inputs,
            String propertyName) {
        Field field = schemaManager.getField(propertyName);
        if (Objects.isNull(field)) {
            log.warn(propertyName + " is not a known property");
            return;
        }
        Type type = field.getType();
        Object propertyValue = documentModel.getPropertyValue(propertyName);
        Property property = documentModel.getProperty(propertyName);
        ObjectResolver objectResolver = getObjectResolver(type);
        if (propertyValue != null) {
            try {
                if (objectResolver instanceof DocumentModelResolver) {
                    if (!coreSession.exists(new IdRef((String) propertyValue))) {
                        return;
                    }
                    DocumentModel doc = coreSession.getDocument(new IdRef((String) propertyValue));

                    outWriter.reset();
                    jg.writeStartObject();
                    enricher.write(jg, doc);
                    jg.writeStringField("title", documentModel.getName());
                    jg.writeEndObject();
                    jg.flush();
                } else {
                    outWriter.reset();
                    writer.write(property, jg);
                }
                propertyValue = MAPPER.readTree(outWriter.toString());
            } catch (IOException e) {
                throw new NuxeoException(e);
            }
        }
        Map<String, Object> input = new HashMap<>();
        input.put("name", propertyName);
        input.put("value", propertyValue);
        if (Objects.nonNull(objectResolver)) {
            input.put("resolver", objectResolver.getName());
        }
        String typeName = type.getName();
        if (type instanceof SimpleTypeImpl && typeName != null && typeName.contains("anonymousType")) {
            typeName = ((SimpleTypeImpl) type).getPrimitiveType().getName();
        }
        input.put("type", typeName);
        input.put("isArray", type instanceof ListTypeImpl);
        inputs.add(input);
    }

    protected ObjectResolver getObjectResolver(Type type) {
        ObjectResolver objectResolver;
        if (type.isListType()) {
            objectResolver = ((ListTypeImpl) type).getType().getObjectResolver();
        } else {
            objectResolver = type.getObjectResolver();
        }
        return objectResolver;
    }

}
