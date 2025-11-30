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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.aws.AWSClientFactory;
import org.nuxeo.ai.aws.dto.DocumentAnalysisResult;
import org.nuxeo.ai.aws.dto.TextractResult;
import org.nuxeo.ai.aws.mapper.TextractMapper;
import org.nuxeo.ai.metrics.AWSMetrics;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

import software.amazon.awssdk.services.textract.model.AnalyzeDocumentRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.Document;
import software.amazon.awssdk.services.textract.model.FeatureType;
import software.amazon.awssdk.services.textract.model.S3Object;

/**
 * Implementation of TextractService - Now using abstraction layer AWS SDK dependencies are isolated to this
 * implementation class only
 *
 * @since 2.1.2
 */
public class TextractServiceImpl extends DefaultComponent implements TextractService {

    public static final String XP_CONFIG = "processor";

    private static final Logger log = LogManager.getLogger(TextractServiceImpl.class);

    protected AWSClientFactory clientFactory;

    protected Map<String, List<TextractProcessor>> processors;

    protected AWSMetrics awsMetrics;

    protected final List<TextractProcessorDescriptor> processorDescriptors = new java.util.ArrayList<>();

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (XP_CONFIG.equals(extensionPoint) && contribution instanceof TextractProcessorDescriptor) {
            processorDescriptors.add((TextractProcessorDescriptor) contribution);
            // Recompute processors map to include newly added descriptor in case it arrives post-start
            processors = processorDescriptors.stream()
                                             .collect(groupingBy(TextractProcessorDescriptor::getServiceName,
                                                     mapping(TextractProcessorDescriptor::getInstance, toList())));
        }
    }

    @Override
    public void start(ComponentContext context) {
        super.start(context);
        clientFactory = Framework.getService(AWSClientFactory.class);
        awsMetrics = Framework.getService(AWSMetrics.class);
        // Build processors map from contributed descriptors grouped by serviceName
        processors = processorDescriptors.stream()
                                         .collect(groupingBy(TextractProcessorDescriptor::getServiceName,
                                                 mapping(TextractProcessorDescriptor::getInstance, toList())));
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        super.stop(context);
        clientFactory = null;
    }

    @Override
    public DocumentAnalysisResult detectText(ManagedBlob blob) {
        if (log.isDebugEnabled()) {
            log.debug("Calling detectDocumentText for " + blob.getKey());
        }

        DetectDocumentTextRequest request = DetectDocumentTextRequest.builder().document(getDocument(blob)).build();

        var awsResponse = clientFactory.getTextractClient().detectDocumentText(request);
        if (log.isDebugEnabled()) {
            log.debug("DetectDocumentTextResponse: " + awsResponse);
        }
        awsMetrics.updateTextractPageUnits(1L);
        TextractResult tr = TextractMapper.mapDetectDocumentTextResponse(awsResponse);
        return toDocumentAnalysisResult(tr);
    }

    @Override
    public DocumentAnalysisResult analyzeDocument(ManagedBlob blob, String... features) {
        if (log.isDebugEnabled()) {
            log.debug("Calling analyzeDocument for " + blob.getKey() + " with features: " + Arrays.toString(features));
        }

        var featureTypes = Arrays.stream(features).map(FeatureType::fromValue).toList();

        AnalyzeDocumentRequest request = AnalyzeDocumentRequest.builder()
                                                               .document(getDocument(blob))
                                                               .featureTypes(featureTypes)
                                                               .build();

        var awsResponse = clientFactory.getTextractClient().analyzeDocument(request);
        if (log.isDebugEnabled()) {
            log.debug("AnalyzeDocumentResponse: " + awsResponse);
        }
        awsMetrics.updateTextractPageUnits(1L);
        TextractResult tr = TextractMapper.mapAnalyzeDocumentResponse(awsResponse);
        return toDocumentAnalysisResult(tr);
    }

    private DocumentAnalysisResult toDocumentAnalysisResult(TextractResult tr) {
        List<DocumentAnalysisResult.Block> blocks = tr.blocks()
                                                      .stream()
                                                      .map(b -> new DocumentAnalysisResult.Block(b.blockType(), // fixed
                                                                                                                // accessor
                                                              b.confidence(), b.text(),
                                                              b.boundingBox() != null
                                                                      ? new DocumentAnalysisResult.BoundingBox(
                                                                              b.boundingBox().width(),
                                                                              b.boundingBox().height(),
                                                                              b.boundingBox().left(),
                                                                              b.boundingBox().top())
                                                                      : null,
                                                              Collections.emptyList()))
                                                      .toList();
        return new DocumentAnalysisResult(blocks);
    }

    @Override
    public <T> List<T> processBlocks(DocumentAnalysisResult result, TextractProcessor<T> processor) {
        if (result == null || result.blocks() == null) {
            return Collections.emptyList();
        }
        return result.blocks()
                     .stream()
                     .map(block -> processor.process(Collections.singletonList(block), null, null, null))
                     .filter(java.util.Objects::nonNull)
                     .toList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<TextractProcessor> getProcessors(String name) {
        return processors != null ? processors.getOrDefault(name, Collections.emptyList()) : Collections.emptyList();
    }

    /**
     * Helper method to create Document object from ManagedBlob
     */
    private Document getDocument(ManagedBlob blob) {
        S3Object s3Object = S3Object.builder().bucket(AWSHelper.getS3BucketName()).name(blob.getKey()).build();

        return Document.builder().s3Object(s3Object).build();
    }
}
