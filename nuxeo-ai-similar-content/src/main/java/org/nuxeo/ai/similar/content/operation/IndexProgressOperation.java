/*
 * (C) Copyright 2006-2020 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ai.similar.content.operation;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ai.bulk.BulkProgressStatus;
import org.nuxeo.ai.similar.content.services.SimilarContentService;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.bulk.BulkService;

@Operation(id = IndexProgressOperation.ID, category = "AI", label = "Get Indexing progress")
public class IndexProgressOperation {

    public static final String ID = "AI.DeduplicationIndexProgress";

    @Context
    protected SimilarContentService scs;

    @Param(name = "id", required = false, description = "BAF job ID")
    protected String id;

    @OperationMethod(asyncService = BulkService.class)
    public BulkProgressStatus run() {
        if (StringUtils.isEmpty(id)) {
            return scs.getStatus();
        } else {
            return scs.getStatus(id);
        }
    }
}
