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
package org.nuxeo.runtime.stream.pipes.streams;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.metrics.NuxeoMetricSet;
import org.nuxeo.runtime.stream.pipes.functions.MetricsProducer;

/**
 * A stream processor using a Function.
 */
public class FunctionStreamProcessor {

    public static final String LOG_IN = "logIn";
    public static final String LOG_OUT = "logOut";
    private static final Log log = LogFactory.getLog(FunctionStreamProcessor.class);

    /**
     * A list of streams for this stream processor
     */
    public static List<String> getStreamsList(Map<String, String> options) {
        List<String> streams = new ArrayList<>(1);
        String logIn = options.get(LOG_IN);
        String logOut = options.get(LOG_OUT);
        if (StringUtils.isBlank(logIn)) {
            throw new IllegalArgumentException("You must define a logIn stream");
        }
        streams.add("i1:" + logIn);
        if (StringUtils.isNotBlank(logOut)) {
            String[] outStreams = logOut.split(",");
            int i = 1;
            for (String outStream : outStreams) {
                streams.add("o" + i++ + ":" + outStream);
            }
        }
        return streams;
    }

    public Topology getTopology(Function<Record, Record> function, Map<String, String> options) {
        List streams = getStreamsList(options);
        String computationName = "func_" + String.join(".", streams);
        NuxeoMetricSet metrics = new NuxeoMetricSet("nuxeo", "pipes", "stream", computationName);
        if (function instanceof MetricsProducer) {
            ((MetricsProducer) function).withMetrics(metrics);
        }
        return Topology.builder()
                       .addComputation(
                               () -> new FunctionComputation(streams.size() - 1,
                                                             computationName, function
                               ), streams)
                       .build();
    }

    /**
     * A Computation that uses a Java Function to transform the Record.
     */
    public static class FunctionComputation extends AbstractComputation {

        private Function<Record, Record> function;

        public FunctionComputation(int outputStreams, String name, Function<Record, Record> function) {

            super(name, 1, outputStreams);
            this.function = function;
        }

        @Override
        public void init(ComputationContext context) {
            log.debug(String.format("Starting computation for %s", metadata.name()));
        }

        @Override
        public void processRecord(ComputationContext context, String inputStreamName, Record record) {
            try {
                writeToStreams(context, function.apply(record));
                context.askForCheckpoint();
            } catch (NuxeoException e) {
                log.error("Discard invalid record: " + record, e);
            }
        }

        @Override
        public void destroy() {
            log.debug(String.format("Destroy computation: %s", metadata.name()));
        }

        /**
         * Writes to the output streams.  Performs no action if no Record or output streams.
         */
        protected void writeToStreams(ComputationContext context, Record record) {
            if (record != null && !metadata.outputStreams().isEmpty()) {
                metadata.outputStreams().forEach(o -> context.produceRecord(o, record));
            }
        }
    }
}
