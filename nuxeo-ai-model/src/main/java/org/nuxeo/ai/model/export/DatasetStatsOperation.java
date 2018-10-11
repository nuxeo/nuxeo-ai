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

import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import java.io.IOException;

@Operation(id = DatasetStatsOperation.ID, category = Constants.CAT_SERVICES, label = "Statistics on a dataset", description = "Return statistics on a set of documents expressed by a NXQL query.")
public class DatasetStatsOperation {

    public static final String ID = "Bulk.DatasetStats";

    @Context
    protected DatasetStatsService service;

    @Context
    protected CoreSession session;

    @Param(name = "query")
    protected String query;

    @Param(name = "inputs")
    protected StringList inputs;

    @Param(name = "outputs")
    protected StringList outputs;

    @OperationMethod
    public Blob run() throws IOException {
        return Blobs.createJSONBlobFromValue(service.getStatistics(session, query, inputs, outputs));
    }
}
