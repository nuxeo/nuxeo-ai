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
package org.nuxeo.ai.enrichment;

import static java.util.Collections.emptyList;
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.toJsonString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.ai.comprehend.ComprehendService;
import org.nuxeo.ai.enrichment.AbstractEnrichmentService;
import org.nuxeo.ai.enrichment.EnrichmentDescriptor;
import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.comprehend.model.DetectSentimentResult;
import com.amazonaws.services.comprehend.model.SentimentScore;
import com.amazonaws.services.comprehend.model.SentimentType;

import net.jodah.failsafe.RetryPolicy;

/**
 * An enrichment service for sentiment analysis
 */
public class SentimentEnrichmentService extends AbstractEnrichmentService {

    public static final String LANGUAGE_CODE = "language";
    protected String languageCode;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        languageCode = descriptor.options.getOrDefault(LANGUAGE_CODE, "en");
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextStream blobTextStream) {
        DetectSentimentResult result;

        try {
            result = Framework.getService(ComprehendService.class).detectSentiment(blobTextStream.getText(), languageCode);
        } catch (AmazonClientException e) {
            throw new NuxeoException(e);
        }

        if (result != null && StringUtils.isNotEmpty(result.getSentiment())) {
            List<EnrichmentMetadata.Label> labels = getSentimentLabel(result);
            String raw = toJsonString(jg -> {
                jg.writeObjectField("sentimentScore", result.getSentimentScore());
                jg.writeStringField("sentiment", result.getSentiment());
            });
            String rawKey = saveJsonAsRawBlob(raw);
            return Collections.singletonList(new EnrichmentMetadata.Builder(kind, name, blobTextStream)
                                                     .withRawKey(rawKey)
                                                     .withLabels(labels)
                                                     .build());
        }
        return emptyList();
    }

    /**
     * Builds a normalized list of labels from the sentiment result
     */
    public List<EnrichmentMetadata.Label> getSentimentLabel(DetectSentimentResult result) {
        List<EnrichmentMetadata.Label> labels = new ArrayList<>(1);
        SentimentScore sentimentScore = result.getSentimentScore();
        SentimentType sentiment;
        try {
            sentiment = SentimentType.valueOf(result.getSentiment().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new NuxeoException(e);
        }

        Float confidence;
        switch (sentiment) {
            case POSITIVE:
                confidence = sentimentScore.getPositive();
                break;
            case NEGATIVE:
                confidence = sentimentScore.getNegative();
                break;
            case MIXED:
                confidence = sentimentScore.getMixed();
                break;
            case NEUTRAL:
                confidence = sentimentScore.getNeutral();
                break;
            default:
                throw new NuxeoException("Invalid sentiment: " + sentiment);
        }

        if (confidence == null) {
            throw new NuxeoException(String.format("A %s sentiment has been returned without any confidence score", sentiment));
        }

        labels.add(new EnrichmentMetadata.Label(sentiment.toString(), confidence / 100));
        return labels;
    }

    @Override
    public RetryPolicy getRetryPolicy() {
        return super.getRetryPolicy()
                    .abortOn(throwable -> throwable.getMessage().contains("is not authorized to perform"));
    }
}
