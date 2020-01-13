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

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_EVALUATION_DATA;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_JOB_ID;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_STATS;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.DATASET_EXPORT_TRAINING_DATA;
import static org.nuxeo.ai.pipes.services.JacksonUtil.MAPPER;
import static org.nuxeo.ai.tensorflow.TFRecordWriter.TFRECORD_MIME_TYPE;
import static org.nuxeo.client.ConstantsV1.API_PATH;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.nuxeo.ai.adapters.DatasetExport;
import org.nuxeo.ai.model.AiDocumentTypeConstants;
import org.nuxeo.client.NuxeoClient;
import org.nuxeo.client.objects.blob.FileBlob;
import org.nuxeo.client.objects.upload.BatchUpload;
import org.nuxeo.client.spi.NuxeoClientException;
import org.nuxeo.client.spi.auth.TokenAuthInterceptor;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.JSONBlob;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

import com.fasterxml.jackson.databind.JsonNode;

import okhttp3.Response;

/**
 * A client that connects to Nuxeo Cloud AI
 */
public class NuxeoCloudClient extends DefaultComponent implements CloudClient {

    public static final String NO_DROP_FLAG = "X-Batch-No-Drop";

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);

    static {
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public static final String XP_CONFIG = "config";

    public static final String API_AI = "ai/";

    private static final Logger log = LogManager.getLogger(NuxeoCloudClient.class);

    protected String projectId;

    protected String url;

    protected NuxeoClient client;

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        List<CloudConfigDescriptor> configs = getDescriptors(XP_CONFIG);
        if (!configs.isEmpty()) {
            if (configs.size() == 1) {
                configureClient(configs.get(0));
            } else {
                throw new IllegalArgumentException("Nuxeo cloud client requires 1 single configuration.");
            }
        }
    }

    protected void configureClient(CloudConfigDescriptor descriptor) {
        NuxeoClient.Builder builder = new NuxeoClient.Builder().url(descriptor.url)
                                                               .readTimeout(descriptor.readTimeout.getSeconds())
                                                               .schemas("dublincore", "common")
                                                               .header(NO_DROP_FLAG, true)
                                                               .connectTimeout(descriptor.connectTimeout.getSeconds());
        if (log.isDebugEnabled()) {
            LogInterceptor logInterceptor = new LogInterceptor();
            builder.interceptor(logInterceptor);
        }
        CloudConfigDescriptor.Authentication auth = descriptor.authentication;
        if (auth != null && isNotEmpty(auth.token)) {
            builder.authentication(new TokenAuthInterceptor(auth.token));
        } else if (auth != null && isNotEmpty(auth.username) && isNotEmpty(auth.password)) {
            builder.authentication(auth.username, auth.password);
        } else {
            throw new IllegalArgumentException("Nuxeo cloud client has incorrect authentication configuration.");
        }
        projectId = descriptor.projectId;
        url = descriptor.url; // The client doesn't seem to export the URL to use
        if (isBlank(url) || isBlank(projectId)) {
            throw new IllegalArgumentException("url and projectId are mandatory fields for cloud configuration.");
        }
        client = builder.connect();
        log.debug("Nuxeo Cloud Client {} is configured for {}.", projectId, url);
    }

    /**
     * Get the configured client
     */
    protected NuxeoClient getClient() {
        if (client == null) {
            throw new IllegalArgumentException(
                    "Nuxeo cloud client has no configuration." + " You should call client.isAvailable() first.");
        }
        return client;
    }

    @Override
    public boolean isAvailable() {
        return client != null;
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

                BatchUpload batchUpload = getClient().batchUploadManager()
                                                     .createBatch()
                                                     .enableChunk()
                                                     .chunkSize(1024 * 1024 * 100);

                FileBlob trainingDataBlob = new FileBlob(trainingData.getFile(), trainingData.getDigest(),
                        TFRECORD_MIME_TYPE);
                FileBlob evalDataBlob = new FileBlob(evalData.getFile(), evalData.getDigest(), TFRECORD_MIME_TYPE);
                FileBlob statsDataBlob = new FileBlob(statsData.getFile(), statsData.getDigest(), TFRECORD_MIME_TYPE);

                // Obliged to use the api in this way (and not in fluent) cause there is an issue in the framework
                // test that truncates the batch id after a first call
                log.info("Uploading Training Dataset of size {} MB",
                        trainingDataBlob.getFile().length() / (1024 * 1024));
                batchUpload.upload("0", trainingDataBlob);
                log.info("Uploading Evaluation Dataset of size {} MB", evalDataBlob.getFile().length() / (1024 * 1024));
                batchUpload.upload("1", evalDataBlob);
                log.info("Uploading Stats Dataset of size {} MB", statsDataBlob.getFile().length() / (1024 * 1024));
                batchUpload.upload("2", statsDataBlob);

                DateTime end = DateTime.now();

                return createDataset(dataset, batchUpload.getBatchId(), start, end);
            } catch (NuxeoClientException e) {
                log.error("Failed to upload dataset. ", e);
            }
        }
        return null;
    }

    @Override
    public void addDatasetToModel(@NotNull DocumentModel doc, String corpusId, String ctxId) {
        DatasetExport dataset = doc.getAdapter(DatasetExport.class);
        if (dataset != null && isNotEmpty(dataset.getModelId()) && isNotEmpty(corpusId)) {
            Path modelsPath = Paths.get(getApiUrl(), API_AI, projectId, dataset.getModelId(), corpusId);
            Response response = getClient().put(modelsPath.toString(), "{}");
            boolean success = response.isSuccessful();
            if (success) {
                log.info("Corpus {} added to model {} added - command or operation {}", corpusId, doc.getId(), ctxId);
            } else {
                log.error("Error when adding corpus {} to model {} - command or operation {} \n Response code {} - {}",
                        corpusId, doc.getId(), ctxId, response.code(), response.message());
            }
        }
    }

    protected String createDataset(DocumentModel datasetDoc, String batchId, DateTime start, DateTime end) {
        String jobId = (String) datasetDoc.getPropertyValue(AiDocumentTypeConstants.DATASET_EXPORT_JOB_ID);
        String query = (String) datasetDoc.getPropertyValue(AiDocumentTypeConstants.DATASET_EXPORT_QUERY);
        Long docCount = (Long) datasetDoc.getPropertyValue(AiDocumentTypeConstants.DATASET_EXPORT_DOCUMENTS_COUNT);
        Long splitProp = (Long) datasetDoc.getPropertyValue(AiDocumentTypeConstants.DATASET_EXPORT_SPLIT);
        long trainingCount = 0;
        long evalCount = 0;
        int split = splitProp == null ? 0 : splitProp.intValue();
        if (docCount != null && docCount > 0 && split > 0) {
            // Estimate counts
            trainingCount = Math.round(docCount * split / 100.f);
            evalCount = Math.round(docCount * (100 - split) / 100.f);
        }

        try {
            // noinspection unchecked
            List<Map<String, Object>> inputs = (List<Map<String, Object>>) datasetDoc.getPropertyValue(
                    AiDocumentTypeConstants.DATASET_EXPORT_INPUTS);
            // noinspection unchecked
            List<Map<String, Object>> outputs = (List<Map<String, Object>>) datasetDoc.getPropertyValue(
                    AiDocumentTypeConstants.DATASET_EXPORT_OUTPUTS);
            List<Map<String, Object>> fields = new ArrayList<>(inputs);
            fields.addAll(outputs);

            String title = makeTitle(trainingCount, evalCount, jobId, fields.size());

            AICorpus.Properties props = new AICorpus.Properties();
            props.setTitle(title);
            props.setDocCount(trainingCount);
            props.setEvaluationDocCount(evalCount);
            props.setQuery(query);
            props.setSplit(split);
            props.setFields(fields);

            props.setTrainData(new AICorpus.Batch("0", batchId));
            props.setEvalData(new AICorpus.Batch("1", batchId));
            props.setStats(new AICorpus.Batch("2", batchId));

            props.setInfo(new AICorpus.Info(dateFormat.format(start.toDate()), dateFormat.format(end.toDate())));

            AICorpus corpus = new AICorpus(jobId, props);

            String payload;
            try (StringWriter writer = new StringWriter()) {
                MAPPER.writeValue(writer, corpus);
                payload = writer.toString();
            }

            log.info("Creating dataset document");

            String url = API_AI + byProjectId("");
            JsonNode node = post(url, payload, (resp) -> {
                if (!resp.isSuccessful()) {
                    log.error(
                            "Failed to create/upload the corpus dataset to project {}, payload {}, url {}, code {} and reason {}",
                            projectId, payload, url, resp.code(), resp.message());
                    return null;
                }
                return MAPPER.readTree(resp.body() != null ? resp.body().byteStream() : null);
            });

            if (node == null || !node.has("uid")) {
                log.error("Failed to create/upload the corpus dataset to project {}, payload {} and response {}",
                        projectId, payload, node);
                return null;
            } else {
                String corpusId = node.get("uid").toString();
                log.info("Corpus {} added to project {}, payload {}", corpusId, projectId, payload);
                return corpusId;
            }

        } catch (IOException e) {
            log.error("Failed to process corpus dataset. ", e);
        }

        return null;
    }

    @Override
    public JSONBlob getCloudAIModels() throws IOException {
        Path modelsPath = Paths.get(getApiUrl(), API_AI, projectId, "models?properties=ai_model");
        Response response = getClient().get(modelsPath.toString());
        if (response.body() == null) {
            log.warn("Could not resolve any AI Models");
            return new JSONBlob("{}");
        }

        String body = response.body().string();
        return new JSONBlob(body);
    }

    @Nullable
    @Override
    public JSONBlob getCorpusDelta(String modelId) throws IOException {
        if (StringUtils.isEmpty(modelId)) {
            throw new NuxeoException("Model Id cannot be empty");
        }

        Path path = Paths.get(getApiUrl(), API_AI, projectId, "model", modelId, "corpusdelta");
        Response response = getClient().get(path.toString());
        if (!response.isSuccessful()) {
            log.error("Failed to obtain Corpus delta of {}. Status code {}", modelId, response.code());
            return null;
        }

        if (response.body() == null) {
            log.warn("Corpus Delta is empty; Model Id {}", modelId);
            return null;
        }

        String body = response.body().string();
        return new JSONBlob(body);
    }

    @Override
    public <T> T post(String postUrl, String jsonBody, ResponseHandler<T> handler) {
        return callCloud(() -> getClient().post(getApiUrl() + postUrl, jsonBody), handler);
    }

    @Override
    public <T> T put(String putUrl, String jsonBody, ResponseHandler<T> handler) {
        return callCloud(() -> getClient().put(getApiUrl() + putUrl, jsonBody), handler);
    }

    @Override
    public <T> T get(String url, ResponseHandler<T> handler) {
        return callCloud(() -> getClient().get(getApiUrl() + url), handler);
    }

    @Override
    public <T> T getByProject(String url, ResponseHandler<T> handler) {
        return get(API_AI + byProjectId(url), handler);
    }

    public <T> T callCloud(Supplier<Response> caller, ResponseHandler<T> handler) {
        Response response = null;
        try {
            if (isAvailable()) {
                response = caller.get();
                if (response != null && handler != null) {
                    return handler.handleResponse(response);
                }
            } else {
                log.warn("Nuxeo cloud client is not configured or unavailable.");
            }
        } catch (IllegalArgumentException iae) {
            log.warn("IllegalArgumentException exception: ", iae);
        } catch (IOException e) {
            log.warn("IOException exception: ", e);
        } finally {
            if (response != null && response.body() != null) {
                response.body().close();
            }
        }
        return null;
    }

    @Override
    public String byProjectId(String url) {
        return projectId + url;
    }

    protected String getApiUrl() {
        return url + API_PATH;
    }

    /**
     * Generate a title for the dataset.
     */
    protected String makeTitle(long trainingCount, long evalCount, String suffix, int numberOfFields) {
        return String.format("%s features, %s Training, %s Evaluation, Export id %s", numberOfFields, trainingCount,
                evalCount, suffix);
    }
}
