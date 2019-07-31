/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_TYPE;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.cloud.CloudClient;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;

@Operation(id = DatasetUploadOperation.ID, category = Constants.CAT_SERVICES, label = "Upload a dataset",
        description = "Uploads the dataset specified in a dataset_export document.")
public class DatasetUploadOperation {

    public static final String ID = "AI.DatasetUpload";

    private static final Logger log = LogManager.getLogger(DatasetUploadOperation.class);

    @Context
    public CoreSession session;

    @Context
    protected CloudClient client;

    @Param(name = "document", description = "A dataset_export document", required = false)
    protected DocumentModel documentModel;

    @OperationMethod
    public void run(DocumentModel document) {
        if (session.getPrincipal().isAdministrator()) {
            if (document != null && DATASET_EXPORT_TYPE.equals(document.getType())) {
                if (client.isAvailable()) {
                    log.info("Uploading dataset to cloud for dataset doc {}", document.getId());

                    String uid = client.uploadedDataset(document);
                    log.info("Upload of dataset to cloud for command {} {}.", document.getId(),
                            isNotEmpty(uid) ? "successful" : "failed");

                    boolean success = client.addDatasetToModel(document, uid);
                    log.info("Added dataset to AI_Model {} for command {} {}.", uid, document.getId(),
                            success ? "successful" : "failed");
                } else {
                    log.warn("Upload to cloud not possible for dataset doc {}, type {} and client {}",
                            document.getId(), document.getType(), client.isAvailable());
                }
            }
        } else {
            log.warn("User {} is not authorised to run the {} operation.", session.getPrincipal().getPrincipalId(), ID);
        }
    }

    @OperationMethod
    public void run(DocumentRef docRef) {
        DocumentModel docModel = session.getDocument(docRef);
        run(docModel);
    }

    @OperationMethod
    public void run() {
        run(documentModel);
    }
}
