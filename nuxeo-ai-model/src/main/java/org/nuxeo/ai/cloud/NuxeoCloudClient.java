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

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_EVALUATION_DATA;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_JOBID;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_STATS;
import static org.nuxeo.ai.model.AiDocumentTypeConstants.CORPUS_TRAINING_DATA;
import static org.nuxeo.ai.tensorflow.TFRecordWriter.TFRECORD_MIME_TYPE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.model.AiDocumentTypeConstants;
import org.nuxeo.ai.pipes.services.JacksonUtil;
import org.nuxeo.client.NuxeoClient;
import org.nuxeo.client.objects.upload.BatchUpload;
import org.nuxeo.client.spi.auth.TokenAuthInterceptor;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

import okhttp3.Response;

/**
 * A client that connects to Nuxeo Cloud AI
 */
public class NuxeoCloudClient extends DefaultComponent implements CloudClient {

    public static final String XP_CONFIG = "config";

    public static final String DATASET_TEMPLATE = "{\n" +
            "  \"entity-type\": \"document\",\n" +
            "  \"name\": \"%s\",\n" +
            "  \"type\": \"AI_Corpus\",\n" +
            "  \"properties\": {\n" +
            "    \"dc:title\": \"%s\",\n" +
            "    \"ai_corpus:documents_count\": %s,\n" +
            "    \"ai_corpus:evaluation_documents_count\": %s,\n" +
            "    \"ai_corpus:query\": \"%s\",\n" +
            "    \"ai_corpus:split\": \"%s\",\n" +
            "    \"ai_corpus:fields\": %s,\n" +
            "    \"ai_corpus:training_data\" : { \"upload-batch\": \"%s\", \"upload-fileId\": \"0\" },\n" +
            "    \"ai_corpus:evaluation_data\" : { \"upload-batch\": \"%s\", \"upload-fileId\": \"1\" },\n" +
            "    \"ai_corpus:statistics\" : { \"upload-batch\": \"%s\", \"upload-fileId\": \"2\" }\n" +
            "  }\n" +
            "}";

    private static final Logger log = LogManager.getLogger(NuxeoCloudClient.class);

    protected String id;

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
        NuxeoClient.Builder builder = new NuxeoClient.Builder().url(descriptor.url);
        CloudConfigDescriptor.Authentication auth = descriptor.authentication;
        if (auth != null && isNotEmpty(auth.token)) {
            builder.authentication(new TokenAuthInterceptor(auth.token));
        } else if (auth != null && isNotEmpty(auth.username) && isNotEmpty(auth.password)) {
            builder.authentication(auth.username, auth.password);
        } else {
            throw new IllegalArgumentException("Nuxeo cloud client has incorrect authentication configuration.");
        }
        client = builder.connect();
        id = descriptor.getId();
        url = descriptor.url; //The client doesn't seem to export the URL to use
        log.debug("Nuxeo Cloud Client {} is configured for {} ", id, url);
    }

    /**
     * Get the configured client
     */
    protected NuxeoClient getClient() {
        if (client == null) {
            throw new IllegalArgumentException("Nuxeo cloud client has no configuration." +
                                                       " You should call client.isAvailable() first.");
        }
        return client;
    }

    @Override
    public boolean isAvailable() {
        return client != null;
    }

    @Override
    public void uploadDataset(DocumentModel corpusDoc) {
        if (corpusDoc != null) {
            String jobId = (String) corpusDoc.getPropertyValue(CORPUS_JOBID);
            Blob trainingData = (Blob) corpusDoc.getPropertyValue(CORPUS_TRAINING_DATA);
            Blob evalData = (Blob) corpusDoc.getPropertyValue(CORPUS_EVALUATION_DATA);
            Blob statsData = (Blob) corpusDoc.getPropertyValue(CORPUS_STATS);

            if (trainingData == null) {
                log.error("Job: {} has no training data.", jobId);
            }
            if (evalData == null) {
                log.error("Job: {} has no evaluation data.", jobId);
            }
            if (statsData == null) {
                log.error("Job: {} has no statistics data.", jobId);
            }
            if (trainingData != null && evalData != null && statsData != null) {
                BatchUpload batchUpload = getClient().batchUploadManager().createBatch();
                batchUpload = batchUpload.upload("0", trainingData.getFile(), trainingData.getDigest(),
                                                 TFRECORD_MIME_TYPE, trainingData.getLength());
                batchUpload = batchUpload.upload("1", evalData.getFile(), evalData.getFilename(),
                                                 TFRECORD_MIME_TYPE, evalData.getLength());
                batchUpload = batchUpload.upload("2", statsData.getFile(), statsData.getFilename(),
                                                 statsData.getMimeType(), statsData.getLength());
                createDataset(corpusDoc, batchUpload.getBatchId());
            }
        }
    }

    protected void createDataset(DocumentModel corpusDoc, String batchId) {
        String jobId = (String) corpusDoc.getPropertyValue(AiDocumentTypeConstants.CORPUS_JOBID);
        String query = (String) corpusDoc.getPropertyValue(AiDocumentTypeConstants.CORPUS_QUERY);
        Long docCount = (Long) corpusDoc.getPropertyValue(AiDocumentTypeConstants.CORPUS_DOCUMENTS_COUNT);
        Long split = (Long) corpusDoc.getPropertyValue(AiDocumentTypeConstants.CORPUS_SPLIT);
        long trainingCount = 0;
        long evalCount = 0;
        if (docCount != null && docCount > 0 && split != null && split > 0) {
            // Estimate counts
            trainingCount = Math.round(docCount * Double.valueOf("0." + split));
            evalCount = Math.round(docCount * Double.valueOf("0." + (100 - split)));
        }
        Response response = null;

        try {
            // noinspection unchecked
            List<Map<String, Object>> inputs =
                    (List<Map<String, Object>>) corpusDoc.getPropertyValue(AiDocumentTypeConstants.CORPUS_INPUTS);
            // noinspection unchecked
            List<Map<String, Object>> outputs =
                    (List<Map<String, Object>>) corpusDoc.getPropertyValue(AiDocumentTypeConstants.CORPUS_OUTPUTS);
            List<Map<String, Object>> fields = new ArrayList<>(inputs);
            fields.addAll(outputs);
            String title = makeTitle(trainingCount, evalCount, jobId, fields.size());
            String fieldsAsJson = JacksonUtil.MAPPER.writeValueAsString(fields);
            String payload = String.format(DATASET_TEMPLATE, jobId, title, trainingCount, evalCount, query,
                                           split, fieldsAsJson, batchId, batchId, batchId);

            log.debug("Uploading to cloud project: {}, payload {}", id, payload);

            response = getClient().post(url + "/api/v1/ai/" + id, payload);

            log.debug("Upload to cloud project: {}, finished.", id);

            if (!response.isSuccessful()) {
                log.error("Failed to upload the corpus dataset. " + response.toString());
            }
        } catch (IOException e) {
            log.error("Failed to process corpus dataset. ", e);
        } finally {
            if (response != null && response.body() != null) {
                response.body().close();
            }
        }
    }

    /**
     * Generate a title for the dataset.
     */
    protected String makeTitle(long trainingCount, long evalCount, String suffix, int numberOfFields) {
        return String.format("%s features, %s Training, %s Evaluation, Export id %s", numberOfFields, trainingCount, evalCount, suffix);
    }
}
