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
package org.nuxeo.ai.cloud;

import java.io.IOException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.nuxeo.ai.sdk.objects.CorporaParameters;
import org.nuxeo.ai.sdk.objects.TensorInstances;
import org.nuxeo.ai.sdk.rest.ResponseHandler;
import org.nuxeo.ai.sdk.rest.client.InsightClient;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * A client that connects to Nuxeo Cloud AI
 */
public interface CloudClient {

    InsightClient getClient();

    /**
     * Is the cloud available to call?
     */
    boolean isAvailable(CoreSession session);

    /**
     * Creates AI_Corpora on Cloud
     *
     * @param corporaId  {@link String} uuid to use
     * @param parameters {@link CorporaParameters} containing query and fields of the corpora
     * @return AI_Corpora uuid as {@link String}
     */
    String initExport(CoreSession session, @Nullable String corporaId, CorporaParameters parameters);

    /**
     * Upload the blobs to the cloud, using data from the corpus document.
     *
     * @return corpus id
     */
    String uploadedDataset(@NotNull DocumentModel dataset);

    /**
     * Bind model and corpora in the cloud
     *
     * @param modelId   of AI_Model
     * @param corporaId of AI_Corpora
     */
    boolean bind(CoreSession session, @Nonnull String modelId, @Nonnull String corporaId);

    /**
     * Notifies Cloud about completion of the Export pipeline
     *
     * @param exportId of BAF command used
     * @return {@link Boolean} as the state of finalization of export on Cloud
     * {@link Boolean#TRUE} if export was found on Cloud and evaluation (if needed) started
     * {@link Boolean#FALSE} otherwise
     */
    boolean notifyOnExportDone(CoreSession session, String exportId);

    String predict(CoreSession session, String modelName, TensorInstances instances)
            throws IOException;

    String predict(String modelName, TensorInstances instances) throws IOException;

    /**
     * @return a list of AI Models retrieved from AI Cloud
     * @throws IOException
     */
    JSONBlob getAllModels(CoreSession session) throws IOException;

    /**
     * @return a list of AI Models filtered by datasource retrieved from AI Cloud
     * @throws IOException
     */
    JSONBlob getModelsByDatasource(CoreSession session) throws IOException;

    /**
     * @return a list of AI Models that's been published retrieved from AI Cloud
     * @throws IOException
     */
    JSONBlob getPublishedModels(CoreSession session) throws IOException;

    /**
     * @param modelId of AI_Model
     * @return JSON representation of corpora delta
     */
    JSONBlob getCorpusDelta(String modelId) throws IOException;

    /*
     * Make a http GET request to the cloud by project path.
     */
    <T> T getByProject(CoreSession session, String url, ResponseHandler<T> handler);

    /**
     * The url for all calls to the cloud prefixed by the project id.
     */
    String byProjectId(String url);

    /**
     * @return the cloud configuration infos
     */
    CloudConfigDescriptor getCloudConfig();
}
