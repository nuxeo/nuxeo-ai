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
import static org.nuxeo.ai.similar.content.pipelines.IndexComputation.INDEX_COMPUTATION_NAME;
import static org.nuxeo.ai.similar.content.utils.PipelineUtils.pipeOf;
import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.STATUS_STREAM;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_1;

import java.util.Map;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.stream.StreamProcessorTopology;

/**
 * BAF Action to Index or Reindex a repository
 */
public class IndexAction implements StreamProcessorTopology {

    public static final String INDEX_ACTION_NAME = "dedup_index_action";

    public static final String INDEX_ACTION_STREAM = INDEX_ACTION_NAME;

    public static final String XPATH_PARAM = "xpath";

    @Override
    public Topology getTopology(Map<String, String> map) {
        return Topology.builder()
                       .addComputation(IndexInitComputation::new,
                               asList(pipeOf(INPUT_1, INDEX_ACTION_STREAM), pipeOf(OUTPUT_1, INDEX_COMPUTATION_NAME)))
                       .addComputation(IndexComputation::new,
                               asList(pipeOf(INPUT_1, INDEX_COMPUTATION_NAME), pipeOf(OUTPUT_1, STATUS_STREAM)))
                       .build();
    }
}
