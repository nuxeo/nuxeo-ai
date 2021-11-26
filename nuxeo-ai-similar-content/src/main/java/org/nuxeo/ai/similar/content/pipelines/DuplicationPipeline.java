/*
 * (C) Copyright 2006-2021 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 * Contributors:
 *    Andrei Nechaev
 *
 */
package org.nuxeo.ai.similar.content.pipelines;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.nuxeo.ai.similar.content.pipelines.DeduplicationScrollerComputation.SCROLLER_COMPUTATION_NAME;
import static org.nuxeo.ai.similar.content.pipelines.DuplicateResolverComputation.RESOLVER_COMPUTE_NAME;
import static org.nuxeo.ai.similar.content.utils.PipelineUtils.pipeOf;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_2;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_1;

import java.util.Map;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.stream.StreamProcessorTopology;

/**
 * Pipeline definition for duplicates processing
 */
public class DuplicationPipeline implements StreamProcessorTopology {

    public static final String PIPELINE_NAME = "ai/similar-content-resolver";

    @Override
    public Topology getTopology(Map<String, String> map) {
        return Topology.builder()
                       .addComputation(() -> new DeduplicationScrollerComputation(SCROLLER_COMPUTATION_NAME),
                               asList(pipeOf(INPUT_1, PIPELINE_NAME), pipeOf(OUTPUT_1, RESOLVER_COMPUTE_NAME)))
                       .addComputation(() -> new DuplicateResolverComputation(RESOLVER_COMPUTE_NAME),
                               singletonList(pipeOf(INPUT_1, RESOLVER_COMPUTE_NAME)))
                       .build();
    }
}
