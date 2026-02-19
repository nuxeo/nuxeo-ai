/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.nuxeo.ai.enrichment;

import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingBlobDigests;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.nuxeo.ai.AWSHelper;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.rekognition.RekognitionService;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.runtime.api.Framework;

import net.jodah.failsafe.RetryPolicy;
import software.amazon.awssdk.core.exception.SdkClientException;

/**
 * Base class for AWS Rekognition enrichment providers that use confidence thresholds. Centralizes confidence parsing,
 * retry policy with SDK exception handling, and blob-digest-based cache keys.
 */
public abstract class AbstractConfidenceEnrichmentProvider extends AbstractEnrichmentProvider
        implements EnrichmentCachable {

    public static final String MINIMUM_CONFIDENCE = "minConfidence";

    protected float minConfidence;

    protected abstract String getDefaultConfidence();

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        minConfidence = parseConfidence(descriptor.options.getOrDefault(MINIMUM_CONFIDENCE, getDefaultConfidence()));
    }

    protected float parseConfidence(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return Float.parseFloat(getDefaultConfidence());
        }
    }

    @FunctionalInterface
    protected interface BlobEnricher {
        Collection<EnrichmentMetadata> process(BlobTextFromDocument doc, String propName, RekognitionService rs,
                ManagedBlob blob);
    }

    /**
     * Iterates over all blobs in the document, delegates per-blob processing to the given enricher, and collects all
     * resulting metadata. Wraps execution in AWS exception handling.
     */
    protected Collection<EnrichmentMetadata> enrichBlobs(BlobTextFromDocument blobTextFromDoc,
            BlobEnricher enricher) {
        return AWSHelper.handlingExceptions(() -> {
            List<EnrichmentMetadata> enriched = new ArrayList<>();
            RekognitionService rs = Framework.getService(RekognitionService.class);
            for (Map.Entry<String, ManagedBlob> blob : blobTextFromDoc.getBlobs().entrySet()) {
                enriched.addAll(enricher.process(blobTextFromDoc, blob.getKey(), rs, blob.getValue()));
            }
            return enriched;
        });
    }

    /**
     * Builds an {@link EnrichmentMetadata} with labels, raw blob key, and document property.
     */
    protected EnrichmentMetadata buildLabelMetadata(BlobTextFromDocument blobTextFromDoc, String propName,
            List<AIMetadata.Label> labels, String rawKey) {
        return new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc).withLabels(asLabels(labels))
                                                                          .withRawKey(rawKey)
                                                                          .withDocumentProperties(
                                                                                  Collections.singleton(propName))
                                                                          .build();
    }

    @SuppressWarnings("unchecked")
    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy().abortOn(throwable -> throwable instanceof SdkClientException
                && throwable.getMessage().contains("is not authorized to perform"));
    }

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingBlobDigests(blobTextFromDoc, name);
    }
}
