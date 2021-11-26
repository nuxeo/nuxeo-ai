/*
 * (C) Copyright 2006-2021 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 * Contributors:
 *    Andrei Nechaev
 *
 */
package org.nuxeo.ai.similar.content.pipelines;

import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ai.sdk.objects.deduplication.SimilarTuple;
import org.nuxeo.ai.similar.content.pojo.SimilarTupleContainer;
import org.nuxeo.ai.similar.content.services.SimilarContentService;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Computation responsible for dealing with duplicates
 */
public class DuplicateResolverComputation extends AbstractComputation {

    protected static final ObjectMapper LOCAL_OM = new ObjectMapper();

    public static final String RESOLVER_COMPUTE_NAME = "ai/dedup-resolver";

    public DuplicateResolverComputation(String name) {
        super(name, 1, 0);
    }

    @Override
    public void processRecord(ComputationContext ctx, String s, Record record) {
        SimilarTupleContainer container;
        try {
            container = LOCAL_OM.readValue(record.getData(), SimilarTupleContainer.class);
        } catch (IOException e) {
            throw new NuxeoException(e);
        }

        SimilarTuple tuple = container.getTuple();

        OperationContext opCtx = new OperationContext();
        opCtx.setInput(new IdRef(tuple.getDocumentId()));
        opCtx.put("similar", tuple.getSimilarDocuments());
        opCtx.put("xpath", tuple.getXpath());

        SimilarContentService scs = Framework.getService(SimilarContentService.class);
        String oid = scs.getOperationID();
        if (StringUtils.isEmpty(oid)) {
            throw new NuxeoException("No Deduplication operation is registered");
        }

        AutomationService automation = Framework.getService(AutomationService.class);
        CoreSession session = CoreInstance.getCoreSessionSystem(container.getRepository(), container.getUser());
        opCtx.setCoreSession(session);
        TransactionHelper.runInNewTransaction(() -> {
            try {
                automation.run(opCtx, oid);
            } catch (OperationException e) {
                throw new NuxeoException(e);
            }
        });
    }
}
