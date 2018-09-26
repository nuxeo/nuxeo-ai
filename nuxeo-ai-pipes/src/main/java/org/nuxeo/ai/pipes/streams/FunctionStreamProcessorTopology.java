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
package org.nuxeo.ai.pipes.streams;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.stream.StreamProcessorTopology;

/**
 * An implementation of a StreamProcessorTopology that makes use of a generic Function<Record, Record>
 */
public interface FunctionStreamProcessorTopology extends StreamProcessorTopology, Function<Record, Optional<Record>> {

    @Override
    default Topology getTopology(Map<String, String> options) {
        if (this instanceof Initializable) {
            ((Initializable) this).init(options);
        }
        return new FunctionStreamProcessor().getTopology(this, options);
    }
}
