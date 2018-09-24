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
package org.nuxeo.ai.bulk;

import static org.nuxeo.ai.bulk.DataSetBulkAction.TRAINING_COMPUTATION_NAME;
import static org.nuxeo.ai.bulk.DataSetExportDoneComputation.ACTION_BLOB_REF;
import static org.nuxeo.ai.bulk.DataSetExportDoneComputation.ACTION_DATA;
import static org.nuxeo.ai.bulk.DataSetExportDoneComputation.ACTION_ID;
import static org.nuxeo.ai.bulk.DataSetExportDoneComputation.ACTION_USERNAME;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_EVALUATION_DATA;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_JOBID;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_TRAINING_DATA;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_TYPE;

import org.nuxeo.ecm.core.api.CloseableCoreSession;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.runtime.transaction.TransactionHelper;
import java.util.List;

/**
 * Listens for when the dataset export is finished.
 */
public class DataSetExportDoneListener implements EventListener {

    @Override
    public void handleEvent(Event event) {
        EventContext ctx = event.getContext();
        if (ctx == null) {
            return;
        }

        String actionId = (String) ctx.getProperty(ACTION_ID);
        String actionData = (String) ctx.getProperty(ACTION_DATA);
        String blobRef = (String) ctx.getProperty(ACTION_BLOB_REF);
        String repository = ctx.getRepositoryName();
        String user = (String) ctx.getProperty(ACTION_USERNAME);
        handleDataSetExportDone(actionId, actionData, blobRef, repository, user);
    }

    protected void handleDataSetExportDone(String actionId, String actionData, String blobRef, String repository, String user) {
        TransactionHelper.runInTransaction(
                () -> {
                    try (CloseableCoreSession session = CoreInstance.openCoreSession(repository, user)) {
                        List<DocumentModel> docs = session.query(String.format("select * from %s WHERE %s = '%s'",
                                                                               CORPUS_TYPE,
                                                                               CORPUS_JOBID,
                                                                               actionId));
                        //Update corpus document
                        for (DocumentModel doc : docs) {
                            doc.setPropertyValue(isTraining(actionData) ? CORPUS_TRAINING_DATA : CORPUS_EVALUATION_DATA, blobRef);
                            session.saveDocument(doc);
                        }
                    }
                }
        );
    }

    protected boolean isTraining(String actionData) {
        return TRAINING_COMPUTATION_NAME.equals(actionData);
    }
}
