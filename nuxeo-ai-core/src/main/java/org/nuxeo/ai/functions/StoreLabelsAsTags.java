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
package org.nuxeo.ai.functions;

import static org.nuxeo.runtime.stream.pipes.events.JacksonUtil.fromRecord;

import java.util.function.Consumer;

import org.nuxeo.ai.enrichment.EnrichmentMetadata;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.platform.tag.TagService;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.pipes.streams.FunctionStreamProcessorTopology;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * A stream processor that saves enrichment labels as tags.
 */
public class StoreLabelsAsTags implements FunctionStreamProcessorTopology {

    @Override
    public Record apply(Record record) {
        EnrichmentMetadata metadata = fromRecord(record, EnrichmentMetadata.class);
        Consumer<EnrichmentMetadata> con =
                meta -> TransactionHelper.runInTransaction(
                        () -> CoreInstance.doPrivileged(meta.repositoryName, session -> {
                            TagService tagService = Framework.getService(TagService.class);
                            meta.getLabels()
                                .forEach(l -> tagService.tag(session, meta.getTargetDocumentRef(), l.getName()));
                        })
                );
        con.accept(metadata);
        return null;
    }

}
