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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import net.jodah.failsafe.Timeout;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ecm.core.api.NuxeoException;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.RetryPolicy;

/**
 * Enriches something using a provider (usually an external provider)
 * AbstractEnrichmentProvider provides a general implementation.
 *
 * @see AbstractEnrichmentProvider
 */
public interface EnrichmentProvider {

    String UNSET = "UNSET_";

    /**
     * The name of the provider
     */
    String getName();

    /**
     * The kind of provider. The kind must match an id of an entry in the "aikind" vocabulary.
     */
    String getKind();

    /**
     * The main enrich method for the provider to implement.  Enriching the blob or text and returning a result.
     */
    Collection<EnrichmentMetadata> enrich(BlobTextFromDocument blobTextFromDoc);

    /**
     * The retry policy for the provider
     */
    @SuppressWarnings("unchecked")
    default RetryPolicy<Collection<AIMetadata>> getRetryPolicy() {
        return new RetryPolicy<Collection<AIMetadata>>()
            .abortOn(NuxeoException.class, FatalEnrichmentError.class)
            .withBackoff(3,36, ChronoUnit.SECONDS)
            .withMaxRetries(2);
    }

    /**
     * The circuit breaker for the provider
     */
    default CircuitBreaker<Collection<AIMetadata>> getCircuitBreaker() {
        return new CircuitBreaker<Collection<AIMetadata>>().withFailureThreshold(8, 9)
                                   .withSuccessThreshold(1)
                                   .withDelay(Duration.of(2, ChronoUnit.MINUTES));
    }

}
