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

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.nuxeo.ai.model.analyzis.DatasetStatsService;
import org.nuxeo.ai.sdk.objects.FieldStatistics;
import org.nuxeo.ai.sdk.objects.PropertyType;
import org.nuxeo.ai.sdk.objects.Statistic;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.Properties;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Type;

@Operation(id = DatasetStatsOperation.ID, category = Constants.CAT_SERVICES, label = "Statistics on a dataset", description = "Return statistics on a set of documents expressed by a NXQL query.")
public class DatasetStatsOperation {

    public static final String ID = "AI.DatasetStats";

    @Context
    protected DatasetStatsService dss;

    @Context
    protected SchemaManager sm;

    @Context
    protected CoreSession session;

    @Param(name = "query")
    protected String query;

    @Param(name = "inputs", required = false)
    protected StringList inputs;

    @Param(name = "inputProperties", required = false)
    protected Properties inputProperties;

    @Param(name = "outputs", required = false)
    protected StringList outputs;

    @Param(name = "outputProperties", required = false)
    protected Properties outputProperties;

    @OperationMethod
    public Blob run() throws IOException {
        Set<PropertyType> inputProp;
        if (inputs != null) {
            inputProp = inputs.stream().map(p -> new PropertyType(p, null)).collect(Collectors.toSet());
        } else if (inputProperties != null) {
            inputProp = inputProperties.entrySet()
                                       .stream()
                                       .map(p -> new PropertyType(p.getKey(), p.getValue()))
                                       .collect(Collectors.toSet());
        } else {
            throw new NuxeoException("inputs or inputProperties, one of the two needs to be defined.");
        }

        Set<PropertyType> outputProp;
        if (outputs != null) {
            outputProp = outputs.stream().map(p -> new PropertyType(p, null)).collect(Collectors.toSet());
        } else if (outputProperties != null) {
            outputProp = outputProperties.entrySet()
                                         .stream()
                                         .map(p -> new PropertyType(p.getKey(), p.getValue()))
                                         .collect(Collectors.toSet());
        } else {
            throw new NuxeoException("outputs or outputProperties, one of the two needs to be defined.");
        }

        Collection<Statistic> statistics = dss.getStatistics(session, query, inputProp, outputProp);
        Set<FieldStatistics> transform = dss.transform(statistics);
        transform.forEach(this::setMultiClass);

        return Blobs.createJSONBlobFromValue(transform);
    }

    protected void setMultiClass(FieldStatistics stat) {
        Type type = sm.getField(stat.getField()).getType();
        stat.setMultiClass(type.isListType());
    }
}
