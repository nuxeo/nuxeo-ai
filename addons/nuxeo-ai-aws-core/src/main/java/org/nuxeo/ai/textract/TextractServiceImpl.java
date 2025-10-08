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
package org.nuxeo.ai.textract;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.metrics.AWSMetrics;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentRequest;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentResponse;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;
import software.amazon.awssdk.services.textract.model.Document;
import software.amazon.awssdk.services.textract.model.FeatureType;

/**
 * Implementation of TextractService
 *
 * @since 2.1.2
 */
public class TextractServiceImpl extends DefaultComponent implements TextractService {

    public static final String XP_CONFIG = "processor";

    private static final Logger log = LogManager.getLogger(TextractServiceImpl.class);

    protected volatile TextractClient client;

    protected Map<String, List<TextractProcessor>> processors;

    protected AWSMetrics awsMetrics;

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        awsMetrics = Framework.getService(AWSMetrics.class);
        List<TextractProcessorDescriptor> configs = getDescriptors(XP_CONFIG);
        if (!configs.isEmpty()) {
            processors = configs.stream()
                                .collect(groupingBy(TextractProcessorDescriptor::getServiceName,
                                        mapping(TextractProcessorDescriptor::getInstance, toList())));
        } else {
            processors = Collections.emptyMap();
        }
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        client = null;
    }

    /**
     * Get the TextractClient client
     */
    protected TextractClient getClient() {
        TextractClient localClient = client;
        if (localClient == null) {
            synchronized (this) {
                localClient = client;
                if (localClient == null) {
                    client = localClient = TextractClient.builder()
                                                        .credentialsProvider(AWSHelper.getInstance().getCredentialsProvider())
                                                        .region(AWSHelper.getInstance().getRegion())
                                                        .build();
                }
            }
        }
        return localClient;
    }

    @Override
    public DetectDocumentTextResponse detectText(ManagedBlob blob) {
        if (log.isDebugEnabled()) {
            log.debug("Calling detectDocumentText for " + blob.getKey());
        }

        Document document = AWSHelper.getInstance().getDocument(blob);
        if (document != null) {
            DetectDocumentTextRequest request = DetectDocumentTextRequest.builder()
                                                                        .document(document)
                                                                        .build();
            DetectDocumentTextResponse result = getClient().detectDocumentText(request);
            awsMetrics.getTextractGlobalCalls().inc();
            if (log.isDebugEnabled()) {
                log.debug("DetectDocumentTextResponse is " + result);
            }
            return result;
        }

        return null;
    }

    @Override
    public AnalyzeDocumentResponse analyzeDocument(ManagedBlob blob, String... features) {
        if (log.isDebugEnabled()) {
            log.debug("Calling analyzeDocument for " + blob.getKey());
        }

        Document document = AWSHelper.getInstance().getDocument(blob);
        if (document != null) {
            AnalyzeDocumentRequest request = AnalyzeDocumentRequest.builder()
                                                                  .featureTypes(Arrays.stream(features)
                                                                      .map(FeatureType::valueOf)
                                                                      .collect(Collectors.toList()))
                                                                  .document(document)
                                                                  .build();
            AnalyzeDocumentResponse result = getClient().analyzeDocument(request);
            if (log.isDebugEnabled()) {
                log.debug("AnalyzeDocumentResponse is " + result);
            }
            awsMetrics.getTextractGlobalCalls().inc();
            return result;
        }
        return null;
    }

    @Override
    public List<TextractProcessor> getProcessors(String serviceName) {
        return processors.getOrDefault(serviceName, Collections.emptyList());
    }

}
