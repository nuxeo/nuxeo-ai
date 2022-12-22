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

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.nuxeo.ai.pipes.services.JacksonUtil.fromRecord;
import static org.nuxeo.ai.pipes.services.JacksonUtil.toRecord;
import static org.nuxeo.ai.pipes.streams.FunctionStreamProcessor.STREAM_IN;
import static org.nuxeo.ai.pipes.streams.FunctionStreamProcessor.STREAM_OUT;
import static org.nuxeo.ai.pipes.streams.FunctionStreamProcessor.buildName;
import static org.nuxeo.ai.pipes.streams.FunctionStreamProcessor.getStreamsList;
import static org.nuxeo.ai.pipes.streams.FunctionStreamProcessor.registerMetrics;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ai.metadata.AIMetadata;
import org.nuxeo.ai.pipes.types.BlobTextFromDocument;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.metrics.NuxeoMetricSet;
import org.nuxeo.runtime.stream.StreamProcessorTopology;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreakerOpenException;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

/**
 * A StreamProcessor that uses an EnrichmentProvider to process the records in stream
 */
public class EnrichingStreamProcessor implements StreamProcessorTopology {

    private static final Logger log = LogManager.getLogger(EnrichingStreamProcessor.class);

    public static final String ENRICHER_NAME = "enrichmentProviderName";

    public static final String USE_CACHE = "cache";

    @Override
    public Topology getTopology(Map<String, String> options) {
        String streamIn = options.get(STREAM_IN);
        String streamOut = options.get(STREAM_OUT);
        List<String> streams = getStreamsList(streamIn, streamOut);
        boolean shouldCache = Boolean.parseBoolean(options.getOrDefault(USE_CACHE, "true"));
        String enricherName = options.get(ENRICHER_NAME);
        if (isBlank(enricherName)) {
            throw new IllegalArgumentException("Please specify valid config for " + ENRICHER_NAME);
        }
        String computationName = buildName(enricherName, streamIn, streamOut);
        EnrichmentMetrics metrics = registerMetrics(new EnrichmentMetrics(computationName), computationName);
        return Topology.builder()
                       .addComputation(
                               () -> new EnrichmentComputation(streams.size() - 1, computationName, enricherName,
                                       metrics, shouldCache), streams)
                       .build();
    }

    /**
     * A Computation that uses an EnrichmentProvider to transform the Record.
     */
    public static class EnrichmentComputation extends AbstractComputation {

        protected final EnrichmentMetrics metrics;

        protected final boolean useCache;

        protected final String enricherName;

        protected EnrichmentProvider provider;

        protected EnrichmentSupport enrichmentSupport;

        protected RetryPolicy retryPolicy;

        protected CircuitBreaker circuitBreaker;

        public EnrichmentComputation(int outputStreams, String computationName, String enricherName,
                EnrichmentMetrics metrics, boolean useCache) {
            super(computationName, 1, outputStreams);
            this.enricherName = enricherName;
            this.metrics = metrics;
            this.useCache = useCache;
        }

        @Override
        public void init(ComputationContext context) {
            log.debug("Starting enrichment computation for {}", metadata.name());
            EnrichmentProvider enrichmentProvider = Framework.getService(AIComponent.class)
                                                             .getEnrichmentProvider(enricherName);
            if (enrichmentProvider == null) {
                log.error("Invalid enricher name " + enricherName);
                throw new IllegalArgumentException("Unknown enrichment provider " + enricherName);
            }
            this.provider = enrichmentProvider;
            if (provider instanceof EnrichmentSupport) {
                this.enrichmentSupport = (EnrichmentSupport) provider;
            } else {
                this.enrichmentSupport = null;
            }
            this.retryPolicy = provider.getRetryPolicy();
            this.circuitBreaker = provider.getCircuitBreaker();
        }

        @Override
        public void processRecord(ComputationContext context, String input, Record record) {
            if (log.isDebugEnabled()) {
                log.debug("Processing record " + record);
            }

            BlobTextFromDocument blobTextFromDoc = fromRecord(record, BlobTextFromDocument.class);
            Callable<Collection<AIMetadata>> callable = getProvider(blobTextFromDoc);

            if (callable != null) {
                metrics.called();
                if (log.isDebugEnabled()) {
                    log.debug("Calling {} for doc {}", enricherName, blobTextFromDoc.getId());
                }
                Collection<AIMetadata> result = null;
                try {
                    result = callProvider(record, callable);
                } catch (CircuitBreakerOpenException e) {
                    metrics.circuitBreaker();
                    // The circuit break is open, throw NuxeoException, so it doesn't continue processing.
                    throw new NuxeoException(
                            "Stream circuit breaker for " + enricherName + ". Stopping processing the stream.");
                } catch (FatalEnrichmentError fee) {
                    metrics.fatal();
                    // Fatal error so throw it to stop processing
                    throw fee;
                } catch (RuntimeException e) {
                    // The error is logged by onFailedAttempt so just move on to the next record.
                }

                if (result != null) {
                    if (useCache && provider instanceof EnrichmentCachable) {
                        EnrichmentCachable cachable = (EnrichmentCachable) provider;
                        cachePut(cachable.getCacheKey(blobTextFromDoc), result, cachable.getTimeToLive());
                    }
                    List<Record> results = result.stream()
                                                 .map(meta -> toRecord(meta.context.documentRef, meta))
                                                 .collect(Collectors.toList());
                    writeToStreams(context, results);
                }
            } else {
                metrics.unsupported();
                log.error("Unsupported call to {} for doc {}", enricherName, blobTextFromDoc.getId());
            }

            context.askForCheckpoint();
        }

        /**
         * Put an entry in the enrichment cache, specify the TTL in seconds.
         */
        protected void cachePut(String cacheKey, Collection<AIMetadata> metadata, long ttl) {
            EnrichmentUtils.cachePut(cacheKey, metadata, ttl);
        }

        /**
         * Get an entry from the enrichment cache
         *
         * @return
         */
        protected Collection<? extends AIMetadata> cacheGet(String cacheKey) {
            return EnrichmentUtils.cacheGet(cacheKey);
        }

        /**
         * Calls the provider using the retryPolicy
         */
        protected Collection<AIMetadata> callProvider(Record record, Callable<Collection<AIMetadata>> callable) {
            return Failsafe.with(retryPolicy).onSuccess(r -> {
                metrics.success();
                if (log.isDebugEnabled()) {
                    log.debug("Enrichment result is " + r);
                }
            }).onFailedAttempt(failure -> {
                metrics.error();
                log.warn("Enrichment error ({}) for record: {} ", enricherName, record, failure);
            }).onRetry(c -> {
                metrics.retry();
                if (log.isDebugEnabled()) {
                    log.debug("Retrying record " + record);
                }
            }).with(circuitBreaker).get(callable);
        }

        /**
         * Try to get a reference to an enrichment provider if the BlobTextFromDocument meets the requirements,
         * otherwise return null,
         */
        protected Callable<Collection<AIMetadata>> getProvider(BlobTextFromDocument blobTextFromDoc) {
            if (useCache && provider instanceof EnrichmentCachable) {
                String cacheKey = ((EnrichmentCachable) provider).getCacheKey(blobTextFromDoc);
                @SuppressWarnings("unchecked")
                Collection<AIMetadata> metadata = (Collection<AIMetadata>) cacheGet(cacheKey);
                if (!metadata.isEmpty()) {
                    metrics.cacheHit();
                    return () -> EnrichmentUtils.copyEnrichmentMetadata(metadata, blobTextFromDoc);
                }
            }
            if (!blobTextFromDoc.getBlobs().isEmpty() && enrichmentSupport != null) {
                for (ManagedBlob blob : blobTextFromDoc.getBlobs().values()) {
                    // Only checks if the first blob matches
                    if (enrichmentSupport.supportsMimeType(blob.getMimeType()) && enrichmentSupport.supportsSize(
                            blob.getLength())) {
                        return () -> getAiMetadata(blobTextFromDoc);
                    } else {
                        log.info("{} does not support a blob with these characteristics {} {}", metadata.name(),
                                blob.getMimeType(), blob.getLength());
                        return null;
                    }
                }
            }
            return () -> getAiMetadata(blobTextFromDoc);
        }

        protected Collection<AIMetadata> getAiMetadata(BlobTextFromDocument blobTextFromDoc) {
            return provider.enrich(blobTextFromDoc).stream().map(m -> (AIMetadata) m).collect(Collectors.toList());
        }

        @Override
        public void destroy() {
            log.debug("Destroy computation: " + metadata.name());
        }

        /**
         * Writes to the output streams. Performs no action if no Record or output streams.
         */
        protected void writeToStreams(ComputationContext context, List<Record> records) {
            if (records != null && !records.isEmpty() && !metadata.outputStreams().isEmpty()) {
                metadata.outputStreams().forEach(o -> {
                    records.forEach(record -> context.produceRecord(o, record));
                    metrics.produced();
                });
            }
        }
    }

    /**
     * Metrics about enrichment providers.
     */
    public static class EnrichmentMetrics extends NuxeoMetricSet {

        protected long called = 0;

        protected long success = 0;

        protected long retries = 0;

        protected long errors = 0;

        protected long fatal = 0;

        protected long circuitBreaker = 0;

        protected long unsupported = 0;

        protected long produced = 0;

        protected long cacheHit = 0;

        public EnrichmentMetrics(String name) {
            super("nuxeo", "ai", "enrichment", name);
            this.putGauge(() -> called, "called");
            this.putGauge(() -> success, "success");
            this.putGauge(() -> retries, "retries");
            this.putGauge(() -> errors, "errors");
            this.putGauge(() -> fatal, "fatal");
            this.putGauge(() -> circuitBreaker, "circuitbreaker");
            this.putGauge(() -> produced, "produced");
            this.putGauge(() -> unsupported, "unsupported");
            this.putGauge(() -> cacheHit, "cacheHit");
        }

        /**
         * Increment called
         */
        public void called() {
            called++;
        }

        /**
         * Increment success
         */
        public void success() {
            success++;
        }

        /**
         * Increment retries
         */
        public void retry() {
            retries++;
        }

        /**
         * Increment errors
         */
        public void error() {
            errors++;
        }

        /**
         * Increment fatal errors
         */
        public void fatal() {
            fatal++;
        }

        /**
         * Increment circuit breakers
         */
        public void circuitBreaker() {
            circuitBreaker++;
        }

        /**
         * Increment unsupported
         */
        public void unsupported() {
            unsupported++;
        }

        /**
         * Increment produced
         */
        public void produced() {
            produced++;
        }

        /**
         * Increment cacheHit
         */
        public void cacheHit() {
            cacheHit++;
        }
    }
}
