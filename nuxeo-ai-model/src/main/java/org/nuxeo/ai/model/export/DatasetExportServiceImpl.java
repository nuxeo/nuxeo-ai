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
package org.nuxeo.ai.model.export;

import static org.nuxeo.ai.AIConstants.EXPORT_ACTION_NAME;
import static org.nuxeo.ai.AIConstants.EXPORT_FEATURES_PARAM;
import static org.nuxeo.ai.AIConstants.EXPORT_SPLIT_PARAM;
import static org.nuxeo.ai.AIConstants.IMAGE_TYPE;
import static org.nuxeo.ai.AIConstants.TEXT_TYPE;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_JOBID;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_TYPE;
import static org.nuxeo.ai.model.ModelProperty.NAME_PROP;
import static org.nuxeo.ai.model.ModelProperty.TYPE_PROP;
import static org.nuxeo.ecm.core.schema.FacetNames.HIDDEN_IN_NAVIGATION;
import static org.nuxeo.ecm.core.schema.TypeConstants.isContentType;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ai.model.AiDocumentTypeConstants;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.bulk.BulkCommand;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exports data
 */
public class DatasetExportServiceImpl extends DefaultComponent implements DatasetExportService {

    public static final PathRef PARENT_PATH = new PathRef("/" + CORPUS_TYPE);

    public static final String NUXEO_FOLDER = "Folder";

    @Override
    public String export(CoreSession session, String nxql,
                         Collection<String> inputProperties, Collection<String> outputProperties, int split) {

        //validate parameters
        if (StringUtils.isBlank(nxql)
                || inputProperties == null || inputProperties.isEmpty()
                || outputProperties == null || outputProperties.isEmpty()) {
            throw new IllegalArgumentException("nxql and properties are required parameters");
        }
        if (split < 1 || split > 100) {
            throw new IllegalArgumentException("Dataset split value is a percentage between 1 and 100");
        }

        List<Map> inputs = inputProperties.stream().map(this::getPropertyWithType).collect(Collectors.toList());
        List<Map> outputs = outputProperties.stream().map(this::getPropertyWithType).collect(Collectors.toList());

        DocumentModel corpus = createCorpus(session, nxql, inputs, outputs, split);

        List<String> featuresList = new ArrayList<>(inputProperties);
        featuresList.addAll(outputProperties);
        BulkCommand bulkCommand = new BulkCommand().withRepository(session.getRepositoryName())
                                                   .withUsername(session.getPrincipal().getName())
                                                   .withQuery(nxql)
                                                   .withAction(EXPORT_ACTION_NAME)
                                                   .withParam(EXPORT_FEATURES_PARAM,
                                                              String.join(",", featuresList))
                                                   .withParam(EXPORT_SPLIT_PARAM, String.valueOf(split));
        String bulkId = Framework.getService(BulkService.class).submit(bulkCommand);
        corpus.setPropertyValue(CORPUS_JOBID, bulkId);
        session.saveDocument(corpus);
        return bulkId;
    }

    /**
     * For the given property, find out if it exists and determine if its text or content
     */
    protected Map<String, Object> getPropertyWithType(String prop) {
        Field field = Framework.getService(SchemaManager.class).getField(prop);
        if (field == null) {
            throw new PropertyNotFoundException(prop + " does not exist.");
        }
        Map<String, Object> feature = new HashMap<>();
        feature.put(NAME_PROP, prop);
        feature.put(TYPE_PROP, isContentType(field.getType()) ? IMAGE_TYPE : TEXT_TYPE);
        return feature;
    }

    /**
     * Create a corpus document for the data export.
     */
    public DocumentModel createCorpus(CoreSession session, String query,
                                      List<Map> inputs, List<Map> outputs, int split) {
        DocumentModel doc = session.createDocumentModel(getRootFolder(session), "corpor1", CORPUS_TYPE);
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_QUERY, query);
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_SPLIT, split);
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_INPUTS, (Serializable) inputs);
        doc.setPropertyValue(AiDocumentTypeConstants.CORPUS_OUTPUTS, (Serializable) outputs);
        return session.createDocument(doc);
    }

    /**
     * Create the root folder if it doesn't exist
     */
    protected String getRootFolder(CoreSession session) {
        if (!session.exists(PARENT_PATH)) {
            DocumentModel doc = session.createDocumentModel("/", CORPUS_TYPE, NUXEO_FOLDER);
            doc.addFacet(HIDDEN_IN_NAVIGATION);
            session.createDocument(doc);
        }
        return PARENT_PATH.toString();
    }


}
