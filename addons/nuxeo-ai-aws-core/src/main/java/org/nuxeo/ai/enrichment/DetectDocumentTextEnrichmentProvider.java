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
package org.nuxeo.ai.enrichment;

import static java.util.Collections.singleton;
import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;
import static org.nuxeo.ai.enrichment.LabelsEnrichmentProvider.MINIMUM_CONFIDENCE;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.pipes.types.PropertyNameType;
import org.nuxeo.ai.textract.TextractProcessor;
import org.nuxeo.ai.textract.TextractService;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;
import com.amazonaws.services.textract.model.Block;
import com.amazonaws.services.textract.model.DetectDocumentTextResult;

import net.jodah.failsafe.RetryPolicy;

/**
 * Detects text in a document.
 * @since 2.1.2
 */
public class DetectDocumentTextEnrichmentProvider extends AbstractEnrichmentProvider implements EnrichmentCachable {

    public static final String DEFAULT_CONFIDENCE = "70";

    private static final Logger log = LogManager.getLogger(DetectDocumentTextEnrichmentProvider.class);

    protected float minConfidence;

    /**
     * Process the Textract response with any available processors.
     */
    protected static void processWithProcessors(BlobTextFromDocument blobTextFromDoc, List<Block> blocks, EnrichmentMetadata.Builder builder, String name) {
        List<TextractProcessor> processors = Framework.getService(TextractService.class).getProcessors(name);
        if (!processors.isEmpty()) {
            TransactionHelper.runInTransaction(
                    () -> CoreInstance.doPrivileged(blobTextFromDoc.getRepositoryName(), session -> {
                        DocumentRef documentRef = new IdRef(blobTextFromDoc.getId());
                        for (TextractProcessor processor : processors) {
                            try {
                                processor.process(blocks, session, documentRef, builder);
                            } catch (NuxeoException e) {
                                log.warn("Textract processing error.", e);
                            }
                        }
                    })
            );
        }
    }

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        minConfidence = Float.parseFloat(descriptor.options.getOrDefault(MINIMUM_CONFIDENCE, DEFAULT_CONFIDENCE));
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {
        return AWSHelper.handlingExceptions(() -> {
            List<EnrichmentMetadata> enriched = new ArrayList<>();
            for (Map.Entry<PropertyNameType, ManagedBlob> blob : blobTextFromDoc.getPropertyBlobs().entrySet()) {
                DetectDocumentTextResult result =
                        Framework.getService(TextractService.class).detectText(blob.getValue());
                if (result != null && !result.getBlocks().isEmpty()) {
                    enriched.addAll(processResults(blobTextFromDoc, blob.getKey().getName(), result.getBlocks()));
                }
            }
            return enriched;
        });
    }

    /**
     * Process the result of the call
     */
    protected Collection<? extends EnrichmentMetadata> processResults(BlobTextFromDocument blobTextFromDoc,
                                                                      String propName,
                                                                      List<Block> blocks) {

        EnrichmentMetadata.Builder builder = new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc);
        processWithProcessors(blobTextFromDoc, blocks, builder, name);
        String raw = toJsonString(jg -> jg.writeObjectField("blocks", blocks));
        String rawKey = saveJsonAsRawBlob(raw);
        return Collections.singletonList(builder
                                                 .withRawKey(rawKey)
                                                 .withDocumentProperties(singleton(propName))
                                                 .build());
    }

    @Override
    public RetryPolicy getRetryPolicy() {
        return new RetryPolicy()
                .abortOn(NuxeoException.class, FatalEnrichmentError.class)
                .withMaxRetries(2)
                .withBackoff(10, 60, TimeUnit.SECONDS);
    }
}
