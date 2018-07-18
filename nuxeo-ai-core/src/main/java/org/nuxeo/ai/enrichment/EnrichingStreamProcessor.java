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

import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.fromRecord;
import static org.nuxeo.runtime.stream.pipes.services.JacksonUtil.toRecord;
import static org.nuxeo.runtime.stream.pipes.streams.FunctionStreamProcessor.STREAM_IN;
import static org.nuxeo.runtime.stream.pipes.streams.FunctionStreamProcessor.STREAM_OUT;
import static org.nuxeo.runtime.stream.pipes.streams.FunctionStreamProcessor.buildName;
import static org.nuxeo.runtime.stream.pipes.streams.FunctionStreamProcessor.getStreamsList;
import static org.nuxeo.runtime.stream.pipes.streams.FunctionStreamProcessor.registerMetrics;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ai.services.AIComponent;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.Computation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.metrics.NuxeoMetricSet;
import org.nuxeo.runtime.stream.StreamProcessorTopology;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

/**
 * A StreamProcessor that uses an EnrichmentService to process the records in stream
 */
public class EnrichingStreamProcessor implements StreamProcessorTopology {

    public static final String ENRICHER_NAME = "enrichmentServiceName";

    private static final Log log = LogFactory.getLog(EnrichingStreamProcessor.class);

    @Override
    public Topology getTopology(Map<String, String> options) {
        String streamIn = options.get(STREAM_IN);
        String streamOut = options.get(STREAM_OUT);
        List<String> streams = getStreamsList(streamIn, streamOut);
        String enricher = options.get(ENRICHER_NAME);
        AIComponent aiComponent = Framework.getService(AIComponent.class);
        EnrichmentService enrichmentService = aiComponent.getEnrichmentService(enricher);
        if (enrichmentService == null) {
            log.error(String.format("Invalid enricher name %s", enricher));
            throw new IllegalArgumentException("Unknown enrichment service " + enricher);
        }
        String computationName = buildName(enricher, streamIn, streamOut);
        EnrichmentMetrics metrics = registerMetrics(new EnrichmentMetrics(computationName), computationName);
        return Topology.builder()
                       .addComputation(addComputation(computationName, enrichmentService, streams, metrics), streams)
                       .build();
    }

    public Supplier<Computation> addComputation(String name, EnrichmentService service,
                                                List<String> streams, EnrichmentMetrics metrics) {
        return () -> new EnrichmentComputation(streams.size() - 1, name, service, metrics);
    }

    /**
     * A Computation that uses an EnrichmentService to transform the Record.
     */
    public static class EnrichmentComputation extends AbstractComputation {

        protected final EnrichmentMetrics metrics;

        private final EnrichmentService service;

        protected RetryPolicy retryPolicy;

        public EnrichmentComputation(int outputStreams, String name, EnrichmentService service, EnrichmentMetrics metrics) {
            super(name, 1, outputStreams);
            this.service = service;
            this.metrics = metrics;
        }

        @Override
        public void init(ComputationContext context) {
            log.debug(String.format("Starting enrichment computation for %s", metadata.name()));
            this.retryPolicy = service.getRetryPolicy();
        }

        @Override
        public void processRecord(ComputationContext context, String inputStreamName, Record record) {
            if (log.isDebugEnabled()) {
                log.debug("Processing record " + record);
            }
            BlobTextStream blobTextStream = fromRecord(record, BlobTextStream.class);
            Callable<Collection<EnrichmentMetadata>> callable = getService(blobTextStream);

            if (callable != null) {
                metrics.called();
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Calling %s for doc %s", service.getName(), blobTextStream.getId()));
                }
                try {
                    Collection<EnrichmentMetadata> result = callService(record, callable);
                    if (result != null) {
                        List<Record> results = result.stream()
                                                     .map(meta -> toRecord(meta.context.documentRef, meta))
                                                     .collect(Collectors.toList());
                        writeToStreams(context, results);
                    }
                } catch (Exception e) {
                    throw new NuxeoException(e);
                }
            } else {
                metrics.unsupported();
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Unsupported call to %s for doc %s", service.getName(), blobTextStream
                            .getId()));
                }
            }
            context.askForCheckpoint();
        }

        /**
         * Calls the service using the retryPolicy
         */
        protected Collection<EnrichmentMetadata> callService(Record record, Callable<Collection<EnrichmentMetadata>> callable) {
            return Failsafe.with(retryPolicy)
                           .onSuccess(r -> {
                               metrics.success();
                               if (log.isDebugEnabled()) {
                                   log.debug("Enrichment result is " + r);
                               }
                           })
                           .onFailure(failure -> log.error("Enrichment failed for record: " + record, failure))
                           .onFailedAttempt(failure -> {
                               metrics.error();
                               log.warn("Enrichment attempt error for record: " + record, failure);
                           })
                           .onRetry(c -> {
                               metrics.retry();
                               if (log.isDebugEnabled()) {
                                   log.debug("Retrying record " + record);
                               }
                           })
                           .get(callable);
        }

        /**
         * Try to get a reference to an enrichment service if the BlobTextStream meets the requirements,
         * otherwise return null,
         */
        protected Callable<Collection<EnrichmentMetadata>> getService(BlobTextStream blobTextStream) {
            ManagedBlob blob = blobTextStream.getBlob();
            if (blob != null &&
                    service.supportsMimeType(blob.getMimeType()) &&
                    service.supportsSize(blob.getLength())) {
                return () -> service.enrich(blobTextStream);
            } else if (blob != null) {
                log.info(String.format("%s does not support a blob with these characteristics %s %s",
                                       metadata.name(), blob.getMimeType(), blob.getLength()
                ));
                return null;
            }
            return () -> service.enrich(blobTextStream);
        }

        @Override
        public void destroy() {
            log.debug(String.format("Destroy computation: %s", metadata.name()));
        }

        /**
         * Writes to the output streams.  Performs no action if no Record or output streams.
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
     * Metrics about enrichment services.
     */
    public static class EnrichmentMetrics extends NuxeoMetricSet {

        protected long called = 0;

        protected long success = 0;

        protected long retries = 0;

        protected long errors = 0;

        protected long unsupported = 0;

        protected long produced = 0;

        public EnrichmentMetrics(String name) {
            super("nuxeo", "streams", "enrichment", name);
            this.putGauge(() -> called, "called");
            this.putGauge(() -> success, "success");
            this.putGauge(() -> retries, "retries");
            this.putGauge(() -> errors, "errors");
            this.putGauge(() -> produced, "produced");
            this.putGauge(() -> unsupported, "unsupported");
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
    }
}
