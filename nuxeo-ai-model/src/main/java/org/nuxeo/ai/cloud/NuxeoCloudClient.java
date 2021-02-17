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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.nuxeo.ai.sdk.objects.AICorpus;
import org.nuxeo.ai.sdk.objects.CorporaParameters;
import org.nuxeo.ai.sdk.objects.TensorInstances;
import org.nuxeo.ai.sdk.rest.ResponseHandler;
import org.nuxeo.ai.sdk.rest.client.API;
import org.nuxeo.ai.sdk.rest.client.Authentication;
import org.nuxeo.ai.sdk.rest.client.InsightClient;
import org.nuxeo.ai.sdk.rest.client.InsightConfiguration;
import org.nuxeo.client.objects.blob.FileBlob;
import org.nuxeo.client.objects.upload.BatchUpload;
import org.nuxeo.client.spi.NuxeoClientException;
import org.nuxeo.client.spi.NuxeoClientRemoteException;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import static org.apache.commons.lang3.StringUtils.isAnyBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_BATCH_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_CORPORA_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_DOCUMENTS_COUNT;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_EVALUATION_DATA;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_INPUTS;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_JOB_ID;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_OUTPUTS;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_QUERY;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_SPLIT;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_STATS;
import static org.nuxeo.ai.adapters.DatasetExport.DATASET_EXPORT_TRAINING_DATA;
import static org.nuxeo.ai.sdk.rest.Common.CORPORA_ID_PARAM;
import static org.nuxeo.ai.sdk.rest.Common.EXPORT_ID_PARAM;
import static org.nuxeo.ai.sdk.rest.Common.MODEL_ID_PARAM;
import static org.nuxeo.ai.sdk.rest.Common.MODEL_NAME_PARAM;
import static org.nuxeo.ai.sdk.rest.api.ModelCaller.DATASOURCE_PARAM;
import static org.nuxeo.ai.sdk.rest.api.ModelCaller.LABEL_PARAM;
import static org.nuxeo.ai.tensorflow.TFRecordWriter.TFRECORD_MIME_TYPE;

/**
 * A client that connects to Nuxeo Cloud AI
 */
public class NuxeoCloudClient extends DefaultComponent implements CloudClient {

    public static final String XP_CONFIG = "config";

    public static final String API_AI = "ai/";

    private static final JSONBlob EMPTY_JSON_BLOB = new JSONBlob("{}");

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);

    private static final int CHUNK_100_MB = 1024 * 1024 * 100;

    private static final Logger log = LogManager.getLogger(NuxeoCloudClient.class);

    static {
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    protected InsightClient client;

    @Override
    public void start(ComponentContext context) {
        super.start(context);

    }

    @Override
    public void activate(ComponentContext context) {
        super.activate(context);
        initClient();
    }

    protected void initClient() {
        CloudConfigDescriptor config = getCloudConfig();
        if (config != null) {
            configureClient(config);
        }
    }

    protected void configureClient(CloudConfigDescriptor descriptor) {
        try {
            if (isAnyBlank(descriptor.url, descriptor.projectId)) {
                throw new IllegalArgumentException("url and projectId are mandatory fields for cloud configuration.");
            }

            String datasource;
            if (StringUtils.isBlank(descriptor.datasource)) {
                datasource = "dev";
                log.warn("Datasource wasn't set; using `{}` as the default", datasource);
            } else {
                datasource = descriptor.datasource;
            }

            CloudConfigDescriptor.Authentication auth = descriptor.authentication;
            Authentication authentication;

            if (auth != null && isNotEmpty(auth.token)) {
                authentication = new Authentication(auth.token);
            } else if (auth != null && isNotEmpty(auth.username) && isNotEmpty(auth.password)) {
                authentication = new Authentication(auth.username, auth.password);
            } else {
                throw new IllegalArgumentException("Nuxeo cloud client has incorrect authentication configuration.");
            }

            InsightConfiguration configuration = new InsightConfiguration.Builder().setAuthentication(authentication)
                                                                                   .setUrl(descriptor.url)
                                                                                   .setConnectionTimeout(
                                                                                           descriptor.connectTimeout)
                                                                                   .setDatasource(datasource)
                                                                                   .setProjectId(descriptor.projectId)
                                                                                   .setReadTimeout(
                                                                                           descriptor.readTimeout)
                                                                                   .setWriteTimeout(
                                                                                           descriptor.writeTimeout)
                                                                                   .build();
            InsightClient client = new InsightClient(configuration);

            this.client = client;
            log.debug("Nuxeo Cloud Client {} is configured for {}.", client.getProjectId(), client.getUrl());
            this.client.connect();
        } catch (NuxeoClientRemoteException e) {
            log.error("Authentication/Connection issue with Insight cloud", e);
        }
    }

    /**
     * Get the configured client
     */
    @Override
    public InsightClient getClient() {
        if (client == null) {
            initClient();
        }
        return client;
    }

    @Override
    public boolean isAvailable() {
        return getClient() != null;
    }

    @Override
    public String initExport(@Nullable String corporaId, CorporaParameters parameters) {
        try {
            return getClient().api(API.Export.INIT)
                              .call(Collections.singletonMap(CORPORA_ID_PARAM, corporaId), parameters);
        } catch (IOException e) {
            log.error(e);
            return null;
        }
    }

    @Override
    public boolean bind(@Nonnull String modelId, @Nonnull String corporaId) {
        try {
            Map<String, Serializable> params = new HashMap<>();
            params.put(MODEL_ID_PARAM, modelId);
            params.put(CORPORA_ID_PARAM, corporaId);
            return Boolean.TRUE.equals(client.api(API.Export.BIND).call(params));
        } catch (IOException e) {
            log.error("Failed to bind model {} with corpora {} for project {}, url {}", modelId, corporaId,
                    client.getProjectId(), client.getUrl(), e);
            return false;
        }
    }

    @Override
    public boolean notifyOnExportDone(String exportId) {
        try {
            return Boolean.TRUE.equals(
                    client.api(API.Export.DONE).call(Collections.singletonMap(EXPORT_ID_PARAM, exportId)));
        } catch (IOException e) {
            log.error("Failed on Export DONE action: export ID {}; project {}; url {}", exportId, client.getProjectId(),
                    client.getUrl(), e);
            return false;
        }
    }

    @Override
    public String uploadedDataset(@NotNull DocumentModel dataset) {
        String jobId = (String) dataset.getPropertyValue(DATASET_EXPORT_JOB_ID);
        Blob trainingData = (Blob) dataset.getPropertyValue(DATASET_EXPORT_TRAINING_DATA);
        Blob evalData = (Blob) dataset.getPropertyValue(DATASET_EXPORT_EVALUATION_DATA);
        Blob statsData = (Blob) dataset.getPropertyValue(DATASET_EXPORT_STATS);

        if (trainingData == null || trainingData.getLength() == 0) {
            log.error("Job/Command: {} has no training data.", jobId);
        } else if (evalData == null || evalData.getLength() == 0) {
            log.error("Job/Command: {} has no evaluation data.", jobId);
        } else if (statsData == null || statsData.getLength() == 0) {
            log.error("Job/Command: {} has no statistics data.", jobId);
        } else {
            try {
                DateTime start = DateTime.now();

                BatchUpload batchUpload = getClient().getBatchUpload(1024 * 1024 * 100);

                String batch1 = batchUpload.getBatchId();

                FileBlob trainingDataBlob = new FileBlob(trainingData.getFile(), trainingData.getDigest(),
                        TFRECORD_MIME_TYPE);
                FileBlob evalDataBlob = new FileBlob(evalData.getFile(), evalData.getDigest(), TFRECORD_MIME_TYPE);
                FileBlob statsDataBlob = new FileBlob(statsData.getFile(), statsData.getDigest(), TFRECORD_MIME_TYPE);

                // Obliged to use the api in this way (and not in fluent) cause there is an issue in the framework
                // test that truncates the batch id after a first call
                log.info("Uploading Training Dataset of size {} MB",
                        trainingDataBlob.getFile().length() / (1024 * 1024));
                batchUpload.upload("0", trainingDataBlob);

                batchUpload = getClient().getBatchUpload(CHUNK_100_MB);

                String batch2 = batchUpload.getBatchId();

                log.info("Uploading Evaluation Dataset of size {} MB", evalDataBlob.getFile().length() / (1024 * 1024));
                batchUpload.upload("1", evalDataBlob);

                batchUpload = getClient().getBatchUpload(CHUNK_100_MB);

                String batch3 = batchUpload.getBatchId();

                log.info("Uploading Stats Dataset of size {} MB", statsDataBlob.getFile().length() / (1024 * 1024));
                batchUpload.upload("2", statsDataBlob);

                DateTime end = DateTime.now();

                AICorpus corpus = createCorpus(dataset, batch1, batch2, batch3, start, end);
                String corporaId = (String) dataset.getPropertyValue(DATASET_EXPORT_CORPORA_ID);
                return uploadDataset(corpus, corporaId);
            } catch (NuxeoClientException e) {
                log.error("Failed to upload dataset. ", e);
            }
        }
        return null;
    }

    protected String uploadDataset(AICorpus corpus, String corporaId) {
        try {
            log.info("Creating dataset document");
            Map<String, Serializable> params = new HashMap<>();
            params.put(CORPORA_ID_PARAM, corporaId);
            return getClient().api(API.Export.ATTACH).call(params, corpus);
        } catch (IOException e) {
            log.error("Failed to process corpus dataset. ", e);
            return null;
        }
    }

    @Nonnull
    private AICorpus createCorpus(DocumentModel datasetDoc, String batch1, String batch2, String batch3, DateTime start,
            DateTime end) {
        String jobId = (String) datasetDoc.getPropertyValue(DATASET_EXPORT_JOB_ID);
        String batchId = (String) datasetDoc.getPropertyValue(DATASET_EXPORT_BATCH_ID);
        String query = (String) datasetDoc.getPropertyValue(DATASET_EXPORT_QUERY);
        Long docCount = (Long) datasetDoc.getPropertyValue(DATASET_EXPORT_DOCUMENTS_COUNT);
        Long splitProp = (Long) datasetDoc.getPropertyValue(DATASET_EXPORT_SPLIT);
        long trainingCount = 0;
        long evalCount = 0;
        int split = splitProp == null ? 0 : splitProp.intValue();
        if (docCount != null && docCount > 0 && split > 0) {
            // Estimate counts
            trainingCount = Math.round(docCount * split / 100.f);
            evalCount = Math.round(docCount * (100 - split) / 100.f);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> inputs = (List<Map<String, Object>>) datasetDoc.getPropertyValue(
                DATASET_EXPORT_INPUTS);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outputs = (List<Map<String, Object>>) datasetDoc.getPropertyValue(
                DATASET_EXPORT_OUTPUTS);
        List<Map<String, Object>> fields = new ArrayList<>(inputs);
        fields.addAll(outputs);

        String title = makeTitle(trainingCount, evalCount, jobId, fields.size());
        AICorpus.Properties props = new AICorpus.Properties.Builder().setTitle(title)
                                                                     .setDocCount(trainingCount)
                                                                     .setEvaluationDocCount(evalCount)
                                                                     .setQuery(query)
                                                                     .setSplit(split)
                                                                     .setFields(fields)
                                                                     .setTrainData(new AICorpus.Batch("0", batch1))
                                                                     .setEvalData(new AICorpus.Batch("1", batch2))
                                                                     .setStats(new AICorpus.Batch("2", batch3))
                                                                     .setInfo(new AICorpus.Info(
                                                                             dateFormat.format(start.toDate()),
                                                                             dateFormat.format(end.toDate())))
                                                                     .setJobId(jobId)
                                                                     .setBatchId(batchId)
                                                                     .build();

        return new AICorpus(jobId, props);
    }

    @Override
    public String predict(String modelName, TensorInstances instances) throws IOException {
        Map<String, Serializable> params = new HashMap<>();
        params.put(MODEL_NAME_PARAM, modelName);
        params.put(DATASOURCE_PARAM, client.getConfiguration().getDatasource());
        return getClient().api(API.Model.PREDICT).call(params, instances);
    }

    @Override
    public JSONBlob getAllModels() throws IOException {
        return getModels(API.Model.ALL, Collections.emptyMap());
    }

    @Override
    public JSONBlob getModelsByDatasource() throws IOException {
        return getModels(API.Model.BY_DATASOURCE, Collections.emptyMap());
    }

    @Override
    public JSONBlob getPublishedModels() throws IOException {
        String datasource = null;
        if (getClient() != null) {
            datasource = client.getConfiguration().getDatasource();
        }
        return getModels(API.Model.PUBLISHED, Collections.singletonMap(LABEL_PARAM, datasource));
    }

    protected JSONBlob getModels(API.Model endpoint, Map<String, Serializable> params) throws IOException {
        if (getClient() == null) {
            log.warn("Attempting to use Insight Client before initialization");
            return EMPTY_JSON_BLOB;
        }

        String response = client.api(endpoint).call(params);
        if (response != null) {
            return new JSONBlob(response);
        } else {
            return EMPTY_JSON_BLOB;
        }
    }

    @Nullable
    @Override
    public JSONBlob getCorpusDelta(String modelId) throws IOException {
        if (StringUtils.isEmpty(modelId)) {
            throw new NuxeoException("Model Id cannot be empty");
        }

        String response = getClient().api(API.Model.DELTA).call(Collections.singletonMap(MODEL_ID_PARAM, modelId));
        if (StringUtils.isEmpty(response)) {
            log.warn("Corpus Delta is empty; Model Id {}", modelId);
            return null;
        }
        return new JSONBlob(response);
    }

    @Override
    public <T> T getByProject(String url, ResponseHandler<T> handler) {
        return getClient().get(API_AI + byProjectId(url), handler);
    }

    @Override
    public String byProjectId(String url) {
        return client.getProjectId() + url;
    }

    @Override
    public CloudConfigDescriptor getCloudConfig() {
        List<CloudConfigDescriptor> configs = getDescriptors(XP_CONFIG);
        if (!configs.isEmpty()) {
            if (configs.size() == 1) {
                return configs.get(0);
            } else {
                throw new IllegalArgumentException("Nuxeo cloud client requires 1 single configuration.");
            }
        }
        return null;
    }

    /**
     * Generate a title for the dataset.
     */
    protected String makeTitle(long trainingCount, long evalCount, String suffix, int numberOfFields) {
        return String.format("%s features, %s Training, %s Evaluation, Export id %s", numberOfFields, trainingCount,
                evalCount, suffix);
    }
}
