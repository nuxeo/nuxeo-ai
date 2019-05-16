/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ai.functions;

import static java.util.Optional.empty;
import static org.nuxeo.ai.pipes.services.JacksonUtil.fromRecord;

import java.util.Optional;
import java.util.function.Consumer;
import org.nuxeo.ai.metadata.SuggestionMetadata;
import org.nuxeo.ai.pipes.streams.FunctionStreamProcessorTopology;
import org.nuxeo.lib.stream.computation.Record;

/**
 * Consumes enrichment suggestions and doesn't return any result.
 */
public abstract class AbstractSuggestionConsumer implements FunctionStreamProcessorTopology, Consumer<SuggestionMetadata> {

    @Override
    public Optional<Record> apply(Record record) {
        SuggestionMetadata metadata = fromRecord(record, SuggestionMetadata.class);
        if (metadata != null) {
            this.accept(metadata);
        }
        return empty();
    }
}
