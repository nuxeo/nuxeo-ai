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
package org.nuxeo.runtime.stream.pipes.pipes;

import static org.nuxeo.runtime.stream.pipes.events.RecordUtil.toRecord;
import static org.nuxeo.runtime.stream.pipes.functions.Predicates.docEvent;
import static org.nuxeo.runtime.stream.pipes.functions.Predicates.notSystem;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.stream.pipes.events.DocEventToStream;
import org.nuxeo.runtime.stream.pipes.functions.FilterFunction;
import org.nuxeo.runtime.stream.pipes.types.BlobTextStream;

/**
 * A function that is only applied to documents
 */
public class DocumentPipeFunction extends FilterFunction<Event, Collection<Record>> {

    static final Function<Event, Collection<BlobTextStream>> func = new DocEventToStream();

    public DocumentPipeFunction() {
        super(docEvent(notSystem().and(d -> !d.hasFacet("Folderish"))),
              e -> {
                  Collection<BlobTextStream> items = func.apply(e);
                  return items.stream().map(i -> toRecord(i.getKey(), i)).collect(Collectors.toList());
              }
        );
    }

}
