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

import static org.nuxeo.ai.enrichment.EnrichmentUtils.makeKeyUsingProperties;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toJsonString;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.comprehend.model.DetectSentimentResult;
import com.amazonaws.services.comprehend.model.SentimentScore;
import com.amazonaws.services.comprehend.model.SentimentType;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ai.comprehend.ComprehendService;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.jodah.failsafe.RetryPolicy;

/**
 * An enrichment service for sentiment analysis
 */
public class SentimentEnrichmentService extends AbstractEnrichmentService implements EnrichmentCachable {

    public static final String LANGUAGE_CODE = "language";

    public static final String DEFAULT_LANGUAGE = "en";

    protected String languageCode;

    @Override
    public void init(EnrichmentDescriptor descriptor) {
        super.init(descriptor);
        languageCode = descriptor.options.getOrDefault(LANGUAGE_CODE, DEFAULT_LANGUAGE);
    }

    @Override
    public Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobTextFromDoc) {

        List<EnrichmentMetadata> enriched = new ArrayList<>();
        try {
            for (Map.Entry<String, String> prop : blobTextFromDoc.getProperties().entrySet()) {
                DetectSentimentResult result = Framework.getService(ComprehendService.class)
                                                        .detectSentiment(prop.getValue(), languageCode);
                if (result != null && StringUtils.isNotEmpty(result.getSentiment())) {
                    enriched.addAll(processResult(blobTextFromDoc, prop.getKey(), result));
                }
            }
            return enriched;
        } catch (AmazonServiceException e) {
            throw EnrichmentHelper.isFatal(e) ? new FatalEnrichmentError(e) : e;
        }
    }

    /**
     * Processes the result of the call to AWS
     */
    protected Collection<EnrichmentMetadata> processResult(BlobTextFromDocument blobTextFromDoc, String propName, DetectSentimentResult result) {
        List<EnrichmentMetadata.Label> labels = getSentimentLabel(result);
        String raw = toJsonString(jg -> {
            jg.writeObjectField("sentimentScore", result.getSentimentScore());
            jg.writeStringField("sentiment", result.getSentiment());
        });
        String rawKey = saveJsonAsRawBlob(raw);
        return Collections.singletonList(new EnrichmentMetadata.Builder(kind, name, blobTextFromDoc)
                                                 .withLabels(labels)
                                                 .withRawKey(rawKey)
                                                 .withDocumentProperties(Collections.singleton(propName))
                                                 .build());
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

    @Override
    public String getCacheKey(BlobTextFromDocument blobTextFromDoc) {
        return makeKeyUsingProperties(blobTextFromDoc, name);
    }

}
